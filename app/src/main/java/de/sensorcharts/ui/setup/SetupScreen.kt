package de.sensorcharts.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.sensorcharts.data.ApiSettings
import de.sensorcharts.data.AppContainer
import kotlinx.coroutines.launch

/** Initial setup and later changes of the PostgREST URL and API key. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    container: AppContainer,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Not saveable on purpose: the API key must not end up in the saved instance state.
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!loaded) {
            container.settings.currentApiSettings()?.let {
                url = it.baseUrl
                apiKey = it.apiKey
            }
            loaded = true
        }
    }

    fun current() = ApiSettings(url.trim(), apiKey.trim())
    val urlValid = url.trim().let { it.startsWith("https://") || it.startsWith("http://") }
    val canSubmit = urlValid && apiKey.isNotBlank() && !busy

    fun testAndMaybeSave(save: Boolean) {
        busy = true
        message = null
        scope.launch {
            try {
                container.api.testConnection(current())
                if (save) {
                    container.settings.saveApiSettings(current())
                    onSaved()
                } else {
                    message = "Verbindung erfolgreich."
                }
            } catch (e: Exception) {
                message = "Verbindung fehlgeschlagen: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (canGoBack) "Verbindung" else "Einrichtung") },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Gib die Adresse deiner PostgREST-API und den API-Key ein. " +
                    "Der Key wird verschlüsselt auf dem Gerät gespeichert.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("PostgREST-URL") },
                placeholder = { Text("https://example.org/api") },
                singleLine = true,
                isError = url.isNotBlank() && !urlValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API-Key (JWT)") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Verbergen" else "Zeigen") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("Verbindung erfolgreich")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                TextButton(onClick = { testAndMaybeSave(save = false) }, enabled = canSubmit) { Text("Testen") }
                Button(onClick = { testAndMaybeSave(save = true) }, enabled = canSubmit) { Text("Speichern") }
            }
        }
    }
}
