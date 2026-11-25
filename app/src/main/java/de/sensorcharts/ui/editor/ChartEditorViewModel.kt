package de.sensorcharts.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.sensorcharts.data.AppContainer
import de.sensorcharts.data.AvailableSeries
import de.sensorcharts.data.ChartConfig
import de.sensorcharts.data.SeriesConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChartEditorViewModel(
    private val container: AppContainer,
    private val chartId: String?,
) : ViewModel() {

    var loaded by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
    var yFromZero by mutableStateOf(false)
    val series = mutableStateListOf<SeriesConfig>()

    /** Series known to the database, for the selection lists. */
    var available by mutableStateOf<List<AvailableSeries>>(emptyList())
        private set
    var availableError by mutableStateOf<String?>(null)
        private set

    val isNew: Boolean get() = chartId == null
    private var original: ChartConfig? = null

    init {
        viewModelScope.launch {
            val chart = chartId?.let { id -> container.settings.charts.first().firstOrNull { it.id == id } }
            original = chart
            title = chart?.title.orEmpty()
            yFromZero = chart?.yFromZero ?: false
            series.addAll(chart?.series.orEmpty())
            loaded = true
        }
        viewModelScope.launch {
            val api = container.settings.currentApiSettings() ?: return@launch
            try {
                available = container.api.availableSeries(api)
            } catch (e: Exception) {
                availableError = e.message ?: e::class.simpleName
            }
        }
    }

    val canSave: Boolean get() = loaded && title.isNotBlank()

    fun upsertSeries(config: SeriesConfig) {
        val index = series.indexOfFirst { it.id == config.id }
        if (index >= 0) series[index] = config else series.add(config)
    }

    fun removeSeries(id: String) {
        series.removeAll { it.id == id }
    }

    fun save(onDone: () -> Unit) {
        val chart = (original ?: ChartConfig(title = title)).copy(
            title = title.trim(),
            series = series.toList(),
            yFromZero = yFromZero,
        )
        viewModelScope.launch {
            container.settings.saveChart(chart)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = chartId ?: return onDone()
        viewModelScope.launch {
            container.settings.deleteChart(id)
            onDone()
        }
    }
}
