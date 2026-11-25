package de.sensorcharts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import de.sensorcharts.data.AppContainer
import de.sensorcharts.ui.dashboard.DashboardScreen
import de.sensorcharts.ui.editor.ChartEditorScreen
import de.sensorcharts.ui.setup.SetupScreen
import de.sensorcharts.ui.theme.SensorChartsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SensorChartsApp).container
        setContent {
            SensorChartsTheme {
                AppNavigation(container)
            }
        }
    }
}

private object Routes {
    const val SETUP = "setup"
    const val DASHBOARD = "dashboard"
    const val EDITOR = "editor?chartId={chartId}"
    fun editor(chartId: String?) = if (chartId == null) "editor" else "editor?chartId=$chartId"
}

@Composable
private fun AppNavigation(container: AppContainer) {
    // Decide once on start whether the initial setup is needed.
    var startRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        startRoute = if (container.settings.currentApiSettings() == null) Routes.SETUP else Routes.DASHBOARD
    }
    val start = startRoute
    if (start == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.SETUP) {
            SetupScreen(
                container = container,
                canGoBack = navController.previousBackStackEntry != null,
                onBack = { navController.popBackStack() },
                onSaved = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.SETUP) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                container = container,
                onOpenSettings = { navController.navigate(Routes.SETUP) },
                onEditChart = { navController.navigate(Routes.editor(it)) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument("chartId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            ChartEditorScreen(
                container = container,
                chartId = entry.arguments?.getString("chartId"),
                onDone = { navController.popBackStack() },
            )
        }
    }
}
