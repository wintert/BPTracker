package com.talwinter.bptracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.talwinter.bptracker.clinical.Guideline
import com.talwinter.bptracker.clinical.MeasurementSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * App settings.
 *
 * The OpenAI key is deliberately NOT in DataStore alongside everything else — it lives in
 * EncryptedSharedPreferences, backed by the Android keystore. Extraction is an optional
 * convenience, so the whole app must work with no key present at all.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val GUIDELINE = stringPreferencesKey("guideline")
        val SETTING = stringPreferencesKey("measurement_setting")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val MODEL = stringPreferencesKey("openai_model")
        val TEXT_SCALE = androidx.datastore.preferences.core.floatPreferencesKey("text_scale")
        val REMINDERS_ON = booleanPreferencesKey("reminders_enabled")
        val MORNING_MIN = androidx.datastore.preferences.core.intPreferencesKey("morning_minute")
        val EVENING_MIN = androidx.datastore.preferences.core.intPreferencesKey("evening_minute")
    }

    /**
     * Multiplied on top of the system font scale, so someone who already runs their phone
     * at large text keeps that and can go further here without touching every other app.
     */
    val textScale: Flow<Float> = context.dataStore.data.map { it[Keys.TEXT_SCALE] ?: 1f }

    suspend fun setTextScale(value: Float) =
        context.dataStore.edit { it[Keys.TEXT_SCALE] = value }.let { }

    // ---- Reminders ----

    val remindersEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.REMINDERS_ON] == true }

    /** Minutes past midnight, so it survives locale and timezone without parsing. */
    val morningMinute: Flow<Int> = context.dataStore.data.map { it[Keys.MORNING_MIN] ?: (7 * 60) }
    val eveningMinute: Flow<Int> = context.dataStore.data.map { it[Keys.EVENING_MIN] ?: (20 * 60) }

    suspend fun setRemindersEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.REMINDERS_ON] = value }.let { }

    suspend fun setReminderTimes(morning: Int, evening: Int) =
        context.dataStore.edit {
            it[Keys.MORNING_MIN] = morning
            it[Keys.EVENING_MIN] = evening
        }.let { }

    /**
     * No default is applied until the user has chosen. Silently classifying against the
     * wrong standard is the failure mode this avoids — see CLINICAL-REFERENCE.md §2b.
     */
    val guideline: Flow<Guideline> = context.dataStore.data.map { prefs ->
        prefs[Keys.GUIDELINE]?.let { runCatching { Guideline.valueOf(it) }.getOrNull() }
            ?: Guideline.ESC_2024
    }

    /** Home by default: every reading this app is built for is taken at home. */
    val measurementSetting: Flow<MeasurementSetting> = context.dataStore.data.map { prefs ->
        prefs[Keys.SETTING]?.let { runCatching { MeasurementSetting.valueOf(it) }.getOrNull() }
            ?: MeasurementSetting.HOME
    }

    val hasOnboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] == true }

    val model: Flow<String> = context.dataStore.data.map { it[Keys.MODEL] ?: DEFAULT_MODEL }

    suspend fun setGuideline(value: Guideline) =
        context.dataStore.edit { it[Keys.GUIDELINE] = value.name }.let { }

    suspend fun setMeasurementSetting(value: MeasurementSetting) =
        context.dataStore.edit { it[Keys.SETTING] = value.name }.let { }

    suspend fun setOnboarded() =
        context.dataStore.edit { it[Keys.ONBOARDED] = true }.let { }

    suspend fun setModel(value: String) =
        context.dataStore.edit { it[Keys.MODEL] = value }.let { }

    // ---- API key, kept apart from ordinary settings ----

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var openAiKey: String?
        get() = securePrefs.getString(KEY_OPENAI, null)?.takeIf { it.isNotBlank() }
        set(value) = securePrefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_OPENAI) else putString(KEY_OPENAI, value.trim())
        }.apply()

    val hasOpenAiKey: Boolean get() = openAiKey != null

    companion object {
        private const val KEY_OPENAI = "openai_api_key"

        /**
         * Cheapest current vision-capable tier. Measured on a real Transtek photo:
         * ~4.4k input + ~300 output tokens, about US$0.0012 per extraction, with 99%
         * confidence on glare-affected seven-segment digits. No reason to pay for more.
         */
        const val DEFAULT_MODEL = "gpt-5.6-luna"

        /** Offered in settings if a monitor ever proves difficult. */
        val AVAILABLE_MODELS = listOf("gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol")
    }
}
