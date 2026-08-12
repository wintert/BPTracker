package com.talwinter.bptracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.talwinter.bptracker.BpApplication
import com.talwinter.bptracker.clinical.Clinical
import com.talwinter.bptracker.clinical.Guideline
import com.talwinter.bptracker.clinical.MeasurementSetting
import com.talwinter.bptracker.clinical.Protocol722
import com.talwinter.bptracker.data.PhotoStore
import com.talwinter.bptracker.data.Reading
import com.talwinter.bptracker.extract.OpenAiExtractor
import com.talwinter.bptracker.reminder.Reminders
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val readings: List<Reading> = emptyList(),
    val guideline: Guideline = Guideline.ESC_2024,
    val setting: MeasurementSetting = MeasurementSetting.HOME,
    val assessment: Protocol722.Assessment? = null,
    val textScale: Float = 1f,
    /**
     * Tri-state on purpose. NavHost reads startDestination exactly once, so a Boolean
     * default would decide the route before DataStore has answered: default true and
     * onboarding is unreachable on a fresh install (and the guideline picker silently
     * never runs), default false and it flashes on every launch. Null means "not known
     * yet" and the UI renders nothing until it resolves.
     */
    val hasOnboarded: Boolean? = null
)

class BpViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as BpApplication
    private val repository = application.repository
    private val settings = application.settings

    private val photoStore = PhotoStore(app)

    val state: StateFlow<HomeState> = combine(
        repository.observeAll(),
        settings.guideline,
        settings.measurementSetting,
        settings.hasOnboarded,
        settings.textScale
    ) { readings, guideline, setting, onboarded, textScale ->
        HomeState(
            readings = readings,
            guideline = guideline,
            setting = setting,
            assessment = Protocol722.assessCurrentWindow(readings, guideline, setting),
            textScale = textScale,
            hasOnboarded = onboarded
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    // ---- Reminders ----
    //
    // Kept out of HomeState: only the settings screen needs them, and HomeState is already
    // combining five flows (the typed combine overloads stop at five).

    data class ReminderState(
        val enabled: Boolean = false,
        val morningMinute: Int = 7 * 60,
        val eveningMinute: Int = 20 * 60
    )

    val reminders: StateFlow<ReminderState> = combine(
        settings.remindersEnabled, settings.morningMinute, settings.eveningMinute
    ) { enabled, morning, evening -> ReminderState(enabled, morning, evening) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderState())

    fun applyReminders(enabled: Boolean, morningMinute: Int, eveningMinute: Int) =
        viewModelScope.launch {
            settings.setRemindersEnabled(enabled)
            settings.setReminderTimes(morningMinute, eveningMinute)
            val context = getApplication<Application>()
            if (enabled) {
                Reminders.schedule(
                    context,
                    LocalTime.ofSecondOfDay(morningMinute * 60L),
                    LocalTime.ofSecondOfDay(eveningMinute * 60L)
                )
            } else {
                Reminders.cancel(context)
            }
        }

    // ---- Extraction ----

    sealed interface ExtractionState {
        data object Idle : ExtractionState
        data object Running : ExtractionState
        data class Done(val outcome: OpenAiExtractor.Outcome, val photoUri: Uri, val exifMillis: Long?) : ExtractionState
    }

    private val _extraction = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extraction: StateFlow<ExtractionState> = _extraction.asStateFlow()

    val hasApiKey: Boolean get() = settings.hasOpenAiKey

    fun extractFrom(uri: Uri, fromGallery: Boolean) {
        val key = settings.openAiKey ?: run {
            _extraction.value = ExtractionState.Done(
                OpenAiExtractor.Outcome.Failed("No API key set — add one in Settings, or just type the numbers."),
                uri,
                null
            )
            return
        }
        _extraction.value = ExtractionState.Running
        viewModelScope.launch {
            val model = settings.model.first()
            val outcome = application.extractor.extract(uri, key, model)
            // Gallery photos carry the real capture time; a fresh camera shot is "now".
            val exif = if (fromGallery) OpenAiExtractor.exifTimestamp(application, uri) else null
            _extraction.value = ExtractionState.Done(outcome, uri, exif)
        }
    }

    fun clearExtraction() { _extraction.value = ExtractionState.Idle }

    // ---- Persistence ----

    /**
     * Copies any attached photo into private storage before saving. Camera captures live
     * in cacheDir (which Android clears) and gallery URIs are grant-scoped and expire, so
     * storing either directly would leave rows pointing at nothing within weeks.
     */
    fun save(reading: Reading, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        onSaved(repository.add(reading.withPersistedPhoto()))
    }

    fun update(reading: Reading) = viewModelScope.launch {
        repository.update(reading.withPersistedPhoto())
    }

    fun delete(reading: Reading) = viewModelScope.launch {
        photoStore.delete(reading.photoUri)
        repository.delete(reading)
    }

    private suspend fun Reading.withPersistedPhoto(): Reading {
        val uri = photoUri ?: return this
        if (uri.startsWith("file://${getApplication<Application>().filesDir}")) return this
        val persisted = photoStore.persist(android.net.Uri.parse(uri)) ?: return copy(photoUri = null)
        return copy(photoUri = persisted.toString())
    }

    fun newCameraTarget() = photoStore.newCameraTarget()

    fun setTextScale(value: Float) = viewModelScope.launch { settings.setTextScale(value) }

    // ---- Settings ----

    fun setGuideline(value: Guideline) = viewModelScope.launch { settings.setGuideline(value) }
    fun setMeasurementSetting(value: MeasurementSetting) = viewModelScope.launch { settings.setMeasurementSetting(value) }
    fun completeOnboarding() = viewModelScope.launch { settings.setOnboarded() }

    fun saveApiKey(key: String?) { settings.openAiKey = key }

    suspend fun exportCsv(): String = repository.exportCsv(state.value.readings)

    fun isCrisis(systolic: Int, diastolic: Int) = Clinical.isCrisis(systolic, diastolic)
}
