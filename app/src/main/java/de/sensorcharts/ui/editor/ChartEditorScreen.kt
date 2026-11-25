package de.sensorcharts.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.sensorcharts.data.AppContainer
import de.sensorcharts.data.AvailableSeries
import de.sensorcharts.data.SeriesConfig
import de.sensorcharts.ui.theme.SeriesColors

private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartEditorScreen(
    container: AppContainer,
    chartId: String?,
    onDone: () -> Unit,
) {
    val vm: ChartEditorViewModel = viewModel(key = "editor-$chartId") { ChartEditorViewModel(container, chartId) }
    var editing by remember { mutableStateOf<SeriesConfig?>(null) }
    var addingNew by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Neuer Chart" else "Chart bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
                actions = {
                    if (!vm.isNew) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Chart löschen") }
                    }
                    IconButton(onClick = { vm.save(onDone) }, enabled = vm.canSave) {
                        Icon(Icons.Filled.Check, "Speichern")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Y-Achse bei 0 beginnen", modifier = Modifier.weight(1f))
                Switch(checked = vm.yFromZero, onCheckedChange = { vm.yFromZero = it })
            }
            HorizontalDivider()
            Text("Datenreihen", style = MaterialTheme.typography.titleMedium)
            if (vm.series.isEmpty()) {
                Text("Noch keine Datenreihen.", style = MaterialTheme.typography.bodyMedium)
            }
            vm.series.forEach { s ->
                SeriesRow(s, onClick = { editing = s }, onRemove = { vm.removeSeries(s.id) })
            }
            OutlinedButton(onClick = { addingNew = true }) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Datenreihe hinzufügen")
            }
            vm.availableError?.let {
                Text(
                    "Liste der verfügbaren Datenreihen konnte nicht geladen werden ($it). " +
                        "Source und Name können trotzdem manuell eingegeben werden.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    val dialogSeries = editing
    if (dialogSeries != null || addingNew) {
        val nextColor = SeriesColors.firstOrNull { c -> vm.series.none { it.color == c } } ?: SeriesColors.first()
        SeriesDialog(
            initial = dialogSeries,
            defaultColor = nextColor,
            available = vm.available,
            onDismiss = { editing = null; addingNew = false },
            onConfirm = {
                vm.upsertSeries(it)
                editing = null
                addingNew = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Chart löschen?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SeriesRow(series: SeriesConfig, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).background(Color(series.color), CircleShape))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(series.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${series.name} · ${series.source}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Datenreihe entfernen") }
        }
    }
}

@Composable
private fun SeriesDialog(
    initial: SeriesConfig?,
    defaultColor: Long,
    available: List<AvailableSeries>,
    onDismiss: () -> Unit,
    onConfirm: (SeriesConfig) -> Unit,
) {
    var source by remember { mutableStateOf(initial?.source.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: defaultColor) }

    val sourceValid = UUID_REGEX.matches(source.trim())
    val sources = remember(available) { available.map { it.source }.distinct() }
    val names = remember(available, source) {
        available.filter { it.source.equals(source.trim(), ignoreCase = true) }.map { it.name }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Datenreihe hinzufügen" else "Datenreihe bearbeiten") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SuggestionField(
                    value = source,
                    onValueChange = { source = it },
                    label = "Source (UUID)",
                    suggestions = sources,
                    isError = source.isNotBlank() && !sourceValid,
                )
                SuggestionField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    suggestions = names,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Bezeichnung (optional)") },
                    placeholder = { Text(name.ifBlank { "z.B. Wohnzimmer" }) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Farbe", style = MaterialTheme.typography.labelLarge)
                ColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = sourceValid && name.isNotBlank(),
                onClick = {
                    val base = initial ?: SeriesConfig(source = "", name = "", color = color)
                    onConfirm(
                        base.copy(
                            source = source.trim().lowercase(),
                            name = name.trim(),
                            label = label.trim(),
                            color = color,
                        ),
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Text field that also offers a filtered list of known values. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = suggestions.filter { it.contains(value.trim(), ignoreCase = true) }.take(50)
    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            trailingIcon = {
                if (suggestions.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeriesColors.forEach { c ->
            val isSelected = c == selected
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(c), CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Filled.Check, "Ausgewählt", tint = Color.White)
            }
        }
    }
}
