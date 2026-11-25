package de.sensorcharts.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Persists the API connection and the chart configuration on the device. */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val apiSettings: Flow<ApiSettings?> = context.dataStore.data
        .map { it.toApiSettings() }
        .distinctUntilChanged()

    val charts: Flow<List<ChartConfig>> = context.dataStore.data
        .map { prefs -> prefs[CHARTS]?.let { decodeCharts(it) } ?: emptyList() }
        .distinctUntilChanged()

    suspend fun currentApiSettings(): ApiSettings? = context.dataStore.data.first().toApiSettings()

    suspend fun saveApiSettings(settings: ApiSettings) {
        context.dataStore.edit {
            it[BASE_URL] = settings.baseUrl
            it[API_KEY] = KeyCipher.encrypt(settings.apiKey)
        }
    }

    suspend fun saveChart(chart: ChartConfig) = updateCharts { charts ->
        val index = charts.indexOfFirst { it.id == chart.id }
        if (index >= 0) charts.toMutableList().apply { set(index, chart) } else charts + chart
    }

    suspend fun deleteChart(id: String) = updateCharts { charts -> charts.filterNot { it.id == id } }

    /** Moves a chart one position up (delta = -1) or down (delta = 1). */
    suspend fun moveChart(id: String, delta: Int) = updateCharts { charts ->
        val from = charts.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in charts.indices) {
            charts
        } else {
            charts.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    private suspend fun updateCharts(transform: (List<ChartConfig>) -> List<ChartConfig>) {
        context.dataStore.edit { prefs ->
            val current = prefs[CHARTS]?.let { decodeCharts(it) } ?: emptyList()
            prefs[CHARTS] = json.encodeToString(transform(current))
        }
    }

    private fun decodeCharts(raw: String): List<ChartConfig> =
        runCatching { json.decodeFromString<List<ChartConfig>>(raw) }.getOrDefault(emptyList())

    private fun Preferences.toApiSettings(): ApiSettings? {
        val url = this[BASE_URL] ?: return null
        val key = this[API_KEY]?.let { KeyCipher.decrypt(it) } ?: return null
        return ApiSettings(url, key)
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val CHARTS = stringPreferencesKey("charts")
    }
}
