package com.talwinter.bptracker

import android.app.Application
import com.talwinter.bptracker.data.AppDatabase
import com.talwinter.bptracker.data.ReadingRepository
import com.talwinter.bptracker.data.SettingsStore
import com.talwinter.bptracker.extract.OpenAiExtractor

/**
 * Manual dependency wiring. A one-user offline app does not need a DI framework, and
 * hand-wiring keeps the whole graph visible in one place.
 */
class BpApplication : Application() {
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: ReadingRepository by lazy { ReadingRepository(AppDatabase.get(this).readingDao()) }
    val extractor: OpenAiExtractor by lazy { OpenAiExtractor(this) }
}
