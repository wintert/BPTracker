package com.talwinter.bptracker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.talwinter.bptracker.clinical.Clinical
import com.talwinter.bptracker.clinical.ReadingValidator
import com.talwinter.bptracker.data.*
import com.talwinter.bptracker.extract.OpenAiExtractor
import com.talwinter.bptracker.extract.ReviewLevel
import com.talwinter.bptracker.ui.BpViewModel
import com.talwinter.bptracker.ui.HomeState
import com.talwinter.bptracker.ui.components.CategoryBadge
import com.talwinter.bptracker.ui.components.CrisisDialog
import java.io.File
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(
    vm: BpViewModel,
    state: HomeState,
    readingId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val extraction by vm.extraction.collectAsState()

    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf(Arm.LEFT) }
    var position by remember { mutableStateOf(BodyPosition.SITTING) }
    var occasion by remember { mutableStateOf(defaultOccasion()) }
    var meds by remember { mutableStateOf(MedicationState.NOT_APPLICABLE) }
    var irregular by remember { mutableStateOf(false) }
    var excludeFromAverages by remember { mutableStateOf(false) }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var source by remember { mutableStateOf(ReadingSource.MANUAL) }
    var confidence by remember { mutableStateOf<Float?>(null) }
    var extractedValues by remember { mutableStateOf<Triple<Int?, Int?, Int?>?>(null) }
    var showCrisis by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<Reading?>(null) }

    // Keyed on the list too: on a cold start the readings may still be empty when this
    // screen composes, and a snapshot read would leave the form blank — saving would then
    // create a duplicate row instead of updating the one being edited.
    LaunchedEffect(readingId, state.readings) {
        if (readingId != null) {
            state.readings.find { it.id == readingId }?.let { r ->
                systolic = r.systolic.toString(); diastolic = r.diastolic.toString()
                pulse = r.pulse?.toString().orEmpty(); notes = r.notes.orEmpty()
                arm = r.arm; position = r.position; occasion = r.occasion; meds = r.medicationState
                irregular = r.irregularHeartbeat; excludeFromAverages = r.excludeFromAverages
                photoUri = r.photoUri?.let(Uri::parse); timestamp = r.timestamp; source = r.source
            }
        }
    }

    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri.value?.let { uri ->
            photoUri = uri; source = ReadingSource.PHOTO_CAMERA
            timestamp = System.currentTimeMillis()
            vm.extractFrom(uri, fromGallery = false)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri; source = ReadingSource.PHOTO_GALLERY
            vm.extractFrom(uri, fromGallery = true)
        }
    }

    // Fold extraction results into the form, leaving anything unread empty for typing.
    LaunchedEffect(extraction) {
        (extraction as? BpViewModel.ExtractionState.Done)?.let { done ->
            (done.outcome as? OpenAiExtractor.Outcome.Success)?.let { ok ->
                val r = ok.result.reading
                r.systolic?.let { systolic = it.toString() }
                r.diastolic?.let { diastolic = it.toString() }
                r.pulse?.let { pulse = it.toString() }
                irregular = ok.result.deviceDisplay.irregularHeartbeat
                if (ok.result.deviceDisplay.indicatesAverageOrMemory) excludeFromAverages = true
                confidence = ok.result.lowestConfidence
                extractedValues = Triple(r.systolic, r.diastolic, r.pulse)
            }
            // Gallery photos carry the true capture time; "now" would misdate old photos.
            done.exifMillis?.let { timestamp = it }
        }
    }

    val sys = systolic.toIntOrNull()
    val dia = diastolic.toIntOrNull()
    val pul = pulse.toIntOrNull()
    val problems = ReadingValidator.validate(sys, dia, pul)
    val canSave = ReadingValidator.canSave(problems)

    fun buildReading() = Reading(
        id = readingId ?: 0,
        timestamp = timestamp,
        systolic = sys!!, diastolic = dia!!, pulse = pul,
        arm = arm, position = position, occasion = occasion, medicationState = meds,
        irregularHeartbeat = irregular, excludeFromAverages = excludeFromAverages,
        notes = notes.takeIf { it.isNotBlank() },
        source = source,
        photoUri = photoUri?.toString(),
        extractionConfidence = confidence,
        wasEditedAfterExtraction = extractedValues?.let { (s, d, p) ->
            s != sys || d != dia || p != pul
        } ?: false
    )

    fun commit(reading: Reading) {
        if (readingId != null) vm.update(reading) else vm.save(reading)
        vm.clearExtraction()
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (readingId != null) "Edit reading" else "Add reading") },
                navigationIcon = {
                    IconButton(onClick = { vm.clearExtraction(); onDone() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (readingId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val (_, uri) = vm.newCameraTarget()
                            cameraUri.value = uri
                            cameraLauncher.launch(uri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("Photo")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.padding(horizontal = 4.dp)); Text("Gallery")
                    }
                }
                if (!vm.hasApiKey) {
                    Text(
                        "Photo reading is off — add an OpenAI key in Settings. You can always type the numbers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (extraction is BpViewModel.ExtractionState.Running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(20.dp))
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("Reading the display…")
                }
            }

            // Photo beside the fields, so values are verified by eye rather than trusted.
            photoUri?.let { uri ->
                Card {
                    AsyncImage(model = uri, contentDescription = "Photo of the monitor",
                        modifier = Modifier.fillMaxWidth().height(220.dp))
                }
            }

            (extraction as? BpViewModel.ExtractionState.Done)?.let { done ->
                when (val outcome = done.outcome) {
                    is OpenAiExtractor.Outcome.Failed -> Notice(outcome.message, error = false)
                    is OpenAiExtractor.Outcome.Success -> {
                        outcome.problems.forEach { p ->
                            Notice(p.message, error = p.level == ReviewLevel.BLOCK)
                        }
                        if (outcome.problems.isEmpty()) {
                            Notice("Read from the photo. Check the numbers before saving.", error = false)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(systolic, { systolic = it }, "Systolic", Modifier.weight(1f))
                NumberField(diastolic, { diastolic = it }, "Diastolic", Modifier.weight(1f))
                NumberField(pulse, { pulse = it }, "Pulse", Modifier.weight(1f))
            }

            ReadingValidator.displayable(problems).forEach { p ->
                Notice(p.message, error = p is ReadingValidator.Problem.Blocking)
            }

            if (sys != null && dia != null && sys > dia) {
                val category = Clinical.classify(sys, dia, state.guideline, state.setting)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category, state.guideline)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("PP ${sys - dia}", style = MaterialTheme.typography.labelSmall)
                }
            }

            ChoiceRow("When", Occasion.entries.map { it to it.label() }, occasion) { occasion = it }
            ChoiceRow("Arm", Arm.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) }, arm) { arm = it }
            ChoiceRow("Position", BodyPosition.entries.map { it to it.name.lowercase().replaceFirstChar(Char::uppercase) }, position) { position = it }
            ChoiceRow("Medication", MedicationState.entries.map { it to it.label() }, meds) { meds = it }

            LabeledSwitch("Irregular heartbeat shown", irregular) { irregular = it }
            LabeledSwitch("Exclude from averages", excludeFromAverages) { excludeFromAverages = it }
            if (excludeFromAverages) {
                Text(
                    "Kept in your history and charts, but left out of the 7-day average. Use this for a " +
                        "stored average read off the monitor's memory.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (stress, illness, poor sleep…)") },
                modifier = Modifier.fillMaxWidth(), minLines = 2
            )

            Text("Time: ${formatWhen(timestamp)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(
                onClick = {
                    val reading = buildReading()
                    if (Clinical.isCrisis(reading.systolic, reading.diastolic)) {
                        pendingSave = reading; showCrisis = true
                    } else commit(reading)
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCrisis && pendingSave != null) {
        CrisisDialog(
            systolic = pendingSave!!.systolic,
            diastolic = pendingSave!!.diastolic,
            onRemeasure = { showCrisis = false; pendingSave = null },
            onSaveAnyway = { commit(pendingSave!!) },
            onDismiss = { showCrisis = false }
        )
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun Notice(message: String, error: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            message,
            Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                    // Material's default selected chip is a lilac secondaryContainer that
                    // belongs to no palette in this app. Pull it back to the green accent.
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Occasion.label() = when (this) {
    Occasion.MORNING -> "Morning"; Occasion.EVENING -> "Evening"; Occasion.OTHER -> "Other"
}

private fun MedicationState.label() = when (this) {
    MedicationState.BEFORE_MEDS -> "Before"; MedicationState.AFTER_MEDS -> "After"
    MedicationState.NOT_APPLICABLE -> "N/A"
}

/** Sensible guess so the common case needs no tapping; always overridable. */
private fun defaultOccasion(): Occasion = when (LocalTime.now().hour) {
    in 4..11 -> Occasion.MORNING
    in 17..23 -> Occasion.EVENING
    else -> Occasion.OTHER
}
