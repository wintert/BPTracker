package com.talwinter.bptracker.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.talwinter.bptracker.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

/**
 * Alarms do not survive a reboot. Without this the reminders silently stop the first time
 * the phone restarts, and the 722 week quietly fails — the worst kind of bug, because
 * nothing appears broken.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        try {
            val settings = SettingsStore(context)
            // Short, bounded reads from DataStore; goAsync() covers the window.
            runBlocking {
                if (!settings.remindersEnabled.first()) return@runBlocking
                Reminders.schedule(
                    context,
                    LocalTime.ofSecondOfDay(settings.morningMinute.first() * 60L),
                    LocalTime.ofSecondOfDay(settings.eveningMinute.first() * 60L)
                )
            }
        } finally {
            pending.finish()
        }
    }
}
