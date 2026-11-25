package de.sensorcharts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme

/** Colors offered for data series (ARGB). Chosen to stay distinguishable in light and dark mode. */
val SeriesColors: List<Long> = listOf(
    0xFF1E88E5, // blau
    0xFFE53935, // rot
    0xFF43A047, // grün
    0xFFFB8C00, // orange
    0xFF8E24AA, // violett
    0xFF00ACC1, // türkis
    0xFFD81B60, // pink
    0xFF7CB342, // hellgrün
    0xFFFDD835, // gelb
    0xFF6D4C41, // braun
    0xFF546E7A, // blaugrau
    0xFF3949AB, // indigo
)

@Composable
fun SensorChartsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme(primary = Color(0xFF90CAF9))
        else -> lightColorScheme(primary = Color(0xFF1565C0))
    }
    MaterialTheme(colorScheme = colorScheme) {
        ProvideVicoTheme(rememberM3VicoTheme(), content)
    }
}
