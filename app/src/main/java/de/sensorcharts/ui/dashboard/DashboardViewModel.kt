package de.sensorcharts.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.sensorcharts.data.ApiSettings
import de.sensorcharts.data.AppContainer
import de.sensorcharts.data.ChartConfig
import de.sensorcharts.data.DataPoint
import de.sensorcharts.data.SeriesConfig
import de.sensorcharts.domain.Period
import de.sensorcharts.domain.TimeWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate

data class SeriesData(val config: SeriesConfig, val points: List<DataPoint>) {
    val average: Double? = if (points.isEmpty()) null else points.sumOf { it.value } / points.size
}

sealed interface ChartState {
    data object Loading : ChartState
    data class Error(val message: String) : ChartState
    data class Loaded(val series: List<SeriesData>) : ChartState
}

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val _window = MutableStateFlow(TimeWindow.current(Period.DAY))
    val window: StateFlow<TimeWindow> = _window.asStateFlow()

    val charts: StateFlow<List<ChartConfig>?> = container.settings.charts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _chartStates = MutableStateFlow<Map<String, ChartState>>(emptyMap())
    val chartStates: StateFlow<Map<String, ChartState>> = _chartStates.asStateFlow()

    private val refreshTrigger = MutableStateFlow(0)
    private val cache = SeriesCache(maxEntries = 200)
    private val requestLimit = Semaphore(4)

    init {
        viewModelScope.launch {
            combine(container.settings.charts, container.settings.apiSettings, _window, refreshTrigger) { charts, api, window, _ ->
                Triple(charts, api, window)
            }.collectLatest { (charts, api, window) ->
                if (api == null) return@collectLatest
                loadAll(charts, api, window)
                prefetch(charts, api, window.previous())
            }
        }
    }

    fun selectPeriod(period: Period) = _window.update { it.withPeriod(period) }
    fun previous() = _window.update { it.previous() }
    fun next() = _window.update { it.next() }
    fun today() = _window.update { TimeWindow.current(it.period, it.zone) }
    fun jumpTo(date: LocalDate) = _window.update { it.copy(startDate = it.period.startOf(date)) }

    fun refresh() {
        cache.clear()
        refreshTrigger.update { it + 1 }
    }

    fun moveChart(id: String, delta: Int) = viewModelScope.launch { container.settings.moveChart(id, delta) }
    fun deleteChart(id: String) = viewModelScope.launch { container.settings.deleteChart(id) }

    private suspend fun loadAll(charts: List<ChartConfig>, api: ApiSettings, window: TimeWindow) = coroutineScope {
        // Charts whose data is fully cached are shown immediately, the others show a spinner.
        _chartStates.value = charts.associate { chart ->
            val cached = chart.series.map { s -> cache.get(key(s, window))?.let { SeriesData(s, it) } }
            chart.id to if (cached.all { it != null }) ChartState.Loaded(cached.filterNotNull()) else ChartState.Loading
        }
        charts.forEach { chart ->
            launch {
                val state = try {
                    ChartState.Loaded(
                        chart.series.map { s -> async { SeriesData(s, load(s, api, window)) } }.awaitAll(),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ChartState.Error(e.message ?: e::class.simpleName ?: "Fehler")
                }
                _chartStates.update { it + (chart.id to state) }
            }
        }
    }

    /** Loads the previous page into the cache so that paging back feels instant. */
    private suspend fun prefetch(charts: List<ChartConfig>, api: ApiSettings, window: TimeWindow) = coroutineScope {
        charts.flatMap { it.series }.distinctBy { key(it, window) }.forEach { s ->
            launch { runCatching { load(s, api, window) } }
        }
    }

    private suspend fun load(series: SeriesConfig, api: ApiSettings, window: TimeWindow): List<DataPoint> {
        val key = key(series, window)
        cache.get(key)?.let { return it }
        val points = requestLimit.withPermit {
            container.api.fetchSeries(
                settings = api,
                resolution = window.period.resolution,
                source = series.source,
                name = series.name,
                from = window.start.toInstant(),
                to = window.end.toInstant(),
            )
        }
        // The current page is still growing, so keep it only briefly.
        cache.put(key, points, ttlMillis = if (window.containsToday()) 2 * 60_000L else null)
        return points
    }

    private fun key(series: SeriesConfig, window: TimeWindow) =
        "${window.period.resolution}|${series.source}|${series.name}|${window.start.toInstant()}|${window.end.toInstant()}"
}

/** Small thread-safe LRU cache for loaded series, with optional expiry per entry. */
private class SeriesCache(private val maxEntries: Int) {
    private class Entry(val points: List<DataPoint>, val expiresAt: Long?)

    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > maxEntries
    }

    @Synchronized
    fun get(key: String): List<DataPoint>? {
        val entry = map[key] ?: return null
        if (entry.expiresAt != null && entry.expiresAt < System.currentTimeMillis()) {
            map.remove(key)
            return null
        }
        return entry.points
    }

    @Synchronized
    fun put(key: String, points: List<DataPoint>, ttlMillis: Long?) {
        map[key] = Entry(points, ttlMillis?.let { System.currentTimeMillis() + it })
    }

    @Synchronized
    fun clear() = map.clear()
}

