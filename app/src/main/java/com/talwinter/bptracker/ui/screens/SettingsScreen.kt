package com.talwinter.bptracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.talwinter.bptracker.clinical.Guideline
import com.talwinter.bptracker.clinical.MeasurementSetting
import com.talwinter.bptracker.ui.BpViewModel
import com.talwinter.bptracker.ui.HomeState
import com.talwinter.bptracker.ui.theme.TextScale
import com.talwinter.bptracker.ui.theme.Type
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BpViewModel, state: HomeState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf(if (vm.hasApiKey) MASKED else "") }
    var keySaved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Section("Text size") {
                Text(
                    "Applied on top of your phone's own text size, so this only changes " +
                        "this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextScale.options.forEach { option ->
                        FilterChip(
                            selected = kotlin.math.abs(state.textScale - option) < 0.01f,
                            onClick = { vm.setTextScale(option) },
                            label = { Text(TextScale.label(option)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Live preview in the app's own reading style, so the choice is judged on
                // the thing it actually affects rather than on this paragraph.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("PREVIEW", style = Type.Eyebrow,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("128/82", style = Type.ReadingMedium)
                    }
                }
            }

            Section("Guideline") {
                Guideline.entries.forEach { g ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.guideline == g, onClick = { vm.setGuideline(g) })
                        Column {
                            Text(g.shortName)
                            Text(g.displayName, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Section("Measurement setting") {
                Text(
                    "Home readings run lower than clinic readings, so they use lower thresholds " +
                        "(135/85 rather than 140/90). Leave this on Home unless you are logging readings " +
                        "taken at a clinic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                MeasurementSetting.entries.forEach { s ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = state.setting == s, onClick = { vm.setMeasurementSetting(s) })
                        Text(s.name.lowercase().replaceFirstChar(Char::uppercase))
                    }
                }
            }

            Section("Photo reading (optional)") {
                Text(
                    "Lets you photograph the monitor instead of typing. Everything works without it — " +
                        "the app never needs a key or a connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; keySaved = false },
                    label = { Text("OpenAI API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (apiKey != MASKED) { vm.saveApiKey(apiKey.ifBlank { null }); keySaved = true }
                    }) { Text("Save key") }
                    OutlinedButton(onClick = { vm.saveApiKey(null); apiKey = ""; keySaved = false }) {
                        Text("Remove")
                    }
                }
                if (keySaved) {
                    Spacer(Modifier.height(4.dp))
                    Text("Saved, encrypted on this device.", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Photos are sent to OpenAI only when you use the photo button, and only to read the " +
                        "numbers. They are stored on this phone, not in your gallery.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section("Your data") {
                Text("${state.readings.size} readings stored on this device.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            val csv = vm.exportCsv()
                            val dir = File(context.cacheDir, "export").apply { mkdirs() }
                            val file = File(dir, "blood-pressure.csv").apply { writeText(csv) }
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/csv"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "Export readings"
                                )
                            )
                        }
                    },
                    enabled = state.readings.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export as CSV") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Take this to a doctor's appointment, or keep it as a backup. Your data is never " +
                        "locked in here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Section("About these thresholds") {
                Text(
                    "Categories follow ${state.guideline.displayName}, applied to " +
                        "${state.setting.name.lowercase()} readings.\n\n" +
                        "One deliberate choice worth knowing: ESC 2024 publishes a home equivalent only for " +
                        "the hypertension threshold (140/90 becomes 135/85). It gives none for the 120/70 " +
                        "boundary, so that one is left unshifted here. That is this app's decision, not a " +
                        "guideline rule.\n\n" +
                        "A single reading does not diagnose anything. The 7-day average — morning and " +
                        "evening, two readings each, day one discarded — is the number that carries meaning.\n\n" +
                        "This app is not a medical device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

private const val MASKED = "••••••••••••••••"
