package de.sensorcharts.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.sensorcharts.data.AppContainer
import de.sensorcharts.data.ChartConfig
import de.sensorcharts.domain.Period
import de.sensorcharts.domain.TimeWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onEditChart: (chartId: String?) -> Unit,
) {
    val vm: DashboardViewModel = viewModel { DashboardViewModel(container) }
    val window by vm.window.collectAsStateWithLifecycle()
    val charts by vm.charts.collectAsStateWithLifecycle()
    val states by vm.chartStates.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Charts") },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Neu laden") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Verbindung") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditChart(null) }) { Icon(Icons.Filled.Add, "Chart hinzufügen") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PeriodHeader(
                window = window,
                onPeriod = vm::selectPeriod,
                onPrevious = vm::previous,
                onNext = vm::next,
                onToday = vm::today,
                onJump = vm::jumpTo,
            )
            val list = charts
            when {
                list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> EmptyState(onAdd = { onEditChart(null) })
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { chart ->
                        ChartCard(
                            chart = chart,
                            state = states[chart.id] ?: ChartState.Loading,
                            window = window,
                            isFirst = chart.id == list.first().id,
                            isLast = chart.id == list.last().id,
                            onEdit = { onEditChart(chart.id) },
                            onMove = { delta -> vm.moveChart(chart.id, delta) },
                            onDelete = { vm.deleteChart(chart.id) },
                            onRetry = vm::refresh,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodHeader(
    window: TimeWindow,
    onPeriod: (Period) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onJump: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Period.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = window.period == period,
                    onClick = { onPeriod(period) },
                    shape = SegmentedButtonDefaults.itemShape(index, Period.entries.size),
                ) { Text(period.label) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Vorheriger Zeitraum")
            }
            Text(
                window.title(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showPicker = true }
                    .padding(vertical = 8.dp),
            )
            IconButton(onClick = onNext, enabled = !window.containsToday()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nächster Zeitraum")
            }
        }
        if (!window.containsToday()) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Zurück zu heute")
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = window.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onJump(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Abbrechen") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun ChartCard(
    chart: ChartConfig,
    state: ChartState,
    window: TimeWindow,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chart.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ChartMenu(isFirst, isLast, onEdit, onMove, onDelete)
            }
            Box(Modifier.fillMaxWidth().padding(end = 8.dp)) {
                when (state) {
                    ChartState.Loading -> Placeholder { CircularProgressIndicator() }
                    is ChartState.Error -> Placeholder {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Fehler beim Laden: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = onRetry) { Text("Erneut versuchen") }
                        }
                    }
                    is ChartState.Loaded -> {
                        val withData = state.series.filter { it.points.isNotEmpty() }
                        when {
                            chart.series.isEmpty() -> Placeholder {
                                TextButton(onClick = onEdit) { Text("Datenreihen hinzufügen") }
                            }
                            withData.isEmpty() -> Placeholder { Text("Keine Daten in diesem Zeitraum") }
                            else -> SensorLineChart(window = window, data = withData, yFromZero = chart.yFromZero)
                        }
                    }
                }
            }
            if (state is ChartState.Loaded && chart.series.isNotEmpty()) {
                Legend(state.series)
            }
        }
    }
}

@Composable
private fun Placeholder(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Legend(series: List<SeriesData>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 8.dp, end = 8.dp),
    ) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Color(s.config.color), CircleShape))
                Spacer(Modifier.width(6.dp))
                val avg = s.average?.let { " · Ø ${formatValue(it)}" } ?: " · keine Daten"
                Text(
                    s.config.displayName + avg,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ChartMenu(
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { open = false; onEdit() })
            if (!isFirst) DropdownMenuItem(text = { Text("Nach oben") }, onClick = { open = false; onMove(-1) })
            if (!isLast) DropdownMenuItem(text = { Text("Nach unten") }, onClick = { open = false; onMove(1) })
            DropdownMenuItem(text = { Text("Löschen") }, onClick = { open = false; confirmDelete = true })
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Chart löschen?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Noch keine Charts angelegt.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Text("Ersten Chart anlegen") }
    }
}
