package de.sensorcharts.ui.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import de.sensorcharts.domain.TimeWindow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

/**
 * Line chart for one page ([window]). The x axis is "minutes since the start of the window",
 * so every page has the same, fixed width regardless of how much data is present.
 * All entries of [data] must contain points.
 */
@Composable
fun SensorLineChart(
    window: TimeWindow,
    data: List<SeriesData>,
    yFromZero: Boolean,
    modifier: Modifier = Modifier,
) {
    val startMillis = window.start.toInstant().toEpochMilli()
    val lengthMinutes = window.lengthMinutes.toDouble()
    val period = window.period
    val xStep = period.resolution.stepMinutes.toDouble()

    val model = remember(data, startMillis) {
        CartesianChartModel(
            LineCartesianLayerModel.build {
                data.forEach { s ->
                    series(
                        // minutes since window start, rounded to 2 decimals
                        x = s.points.map { round((it.epochMillis - startMillis) / 600.0) / 100.0 },
                        y = s.points.map { it.value },
                        key = s.config.id,
                    )
                }
            },
        )
    }

    val colors = data.associate { it.config.id to it.config.color }
    val lineProvider = remember(colors) {
        val lines = colors.mapValues { (_, color) ->
            LineCartesianLayer.Line(
                fill = LineCartesianLayer.LineFill.single(Fill(Color(color))),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 1.5.dp),
            )
        }
        LineCartesianLayer.LineProvider { key, _, _ -> lines[key] ?: lines.values.first() }
    }
    val rangeProvider = remember(lengthMinutes, yFromZero) { WindowRangeProvider(lengthMinutes, yFromZero) }

    val xFormatter = remember(window) {
        CartesianValueFormatter { _, x, _ ->
            window.start.plusMinutes(x.toLong()).format(period.axisFormatter)
        }
    }
    val yFormatter = remember { CartesianValueFormatter { _, y, _ -> formatValue(y) } }
    val labelSpacing = (period.labelIntervalMinutes / period.resolution.stepMinutes).coerceAtLeast(1)

    val markerLabel = rememberTextComponent(
        style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
        padding = Insets(start = 6.dp, top = 3.dp, end = 6.dp, bottom = 3.dp),
        background = rememberShapeComponent(
            fill = Fill(MaterialTheme.colorScheme.surfaceContainerHighest),
            shape = RoundedCornerShape(4.dp),
        ),
    )

    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(lineProvider = lineProvider, rangeProvider = rangeProvider),
        startAxis = VerticalAxis.rememberStart(valueFormatter = yFormatter),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = xFormatter,
            itemPlacer = remember(labelSpacing) {
                HorizontalAxis.ItemPlacer.aligned(spacing = { labelSpacing }, addExtremeLabelPadding = false)
            },
        ),
        marker = rememberDefaultCartesianMarker(label = markerLabel),
        getXStep = { _, _, _ -> xStep },
    )

    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier.fillMaxWidth(),
        scrollState = rememberVicoScrollState(),
        zoomState = rememberVicoZoomState(initialZoom = Zoom.Content, minZoom = Zoom.Content),
        chartAreaHeight = 220.dp,
    )
}

/** Fixed x range = the whole window; y range = data range with a little headroom, rounded. */
private class WindowRangeProvider(
    private val lengthMinutes: Double,
    private val yFromZero: Boolean,
) : CartesianLayerRangeProvider {
    override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) = 0.0
    override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) = lengthMinutes

    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        if (yFromZero && minY >= 0) return 0.0
        val step = niceStep(minY, maxY)
        return floor((minY - padding(minY, maxY)) / step) * step
    }

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        if (yFromZero && maxY <= 0) return 0.0
        val step = niceStep(minY, maxY)
        return ceil((maxY + padding(minY, maxY)) / step) * step
    }

    private fun padding(minY: Double, maxY: Double) =
        if (maxY > minY) (maxY - minY) * 0.05 else max(abs(maxY) * 0.05, 0.5)

    private fun niceStep(minY: Double, maxY: Double): Double {
        val span = max(maxY - minY, max(abs(maxY) * 0.1, 1.0))
        return 10.0.pow(floor(log10(span)) - 1)
    }

    override fun equals(other: Any?) =
        other is WindowRangeProvider && other.lengthMinutes == lengthMinutes && other.yFromZero == yFromZero

    override fun hashCode() = 31 * lengthMinutes.hashCode() + yFromZero.hashCode()
}

private val valueFormat = ThreadLocal.withInitial {
    DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.GERMAN))
}

fun formatValue(value: Double): String = valueFormat.get()!!.format(value)
