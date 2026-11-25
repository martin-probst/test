package de.sensorcharts.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Connection to the PostgREST API. */
data class ApiSettings(
    val baseUrl: String,
    val apiKey: String,
)

/** A chart on the dashboard, configured by the user. */
@Serializable
data class ChartConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val series: List<SeriesConfig> = emptyList(),
    /** Start the y axis at 0 instead of fitting it to the data range. */
    val yFromZero: Boolean = false,
)

/**
 * One line in a chart: the values of [name] measured by [source].
 * [color] is an ARGB value.
 */
@Serializable
data class SeriesConfig(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val name: String,
    val label: String = "",
    val color: Long,
) {
    val displayName: String get() = label.ifBlank { name }
}

/** A series available in the database (from the event_series view). */
data class AvailableSeries(
    val source: String,
    val name: String,
)

/** A measured (or aggregated) value. */
data class DataPoint(
    val epochMillis: Long,
    val value: Double,
)
