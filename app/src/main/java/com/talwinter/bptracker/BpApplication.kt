package com.talwinter.bptracker

import android.app.Application
import com.talwinter.bptracker.data.AppDatabase
import com.talwinter.bptracker.data.ReadingRepository
import com.talwinter.bptracker.data.SettingsStore
import com.talwinter.bptracker.extract.OpenAiExtractor
import com.talwinter.bptracker.reminder.Reminders

/**
 * Manual dependency wiring. A one-user offline app does not need a DI framework, and
 * hand-wiring keeps the whole graph visible in one place.
 */
class BpApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Idempotent, and the only thing that reliably migrates the notification channel
        // after an upgrade. Channel importance is immutable once created, so raising it
        // means shipping a new channel id — but nothing recreated the channel on launch,
        // so the change only took effect the next time reminder settings were touched or
        // a reminder fired. Doing it here means an upgrade heals immediately.
        Reminders.ensureChannel(this)
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: ReadingRepository by lazy { ReadingRepository(AppDatabase.get(this).readingDao()) }
    val extractor: OpenAiExtractor by lazy { OpenAiExtractor(this) }
}
