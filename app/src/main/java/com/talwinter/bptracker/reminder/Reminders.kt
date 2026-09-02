package com.talwinter.bptracker.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.talwinter.bptracker.MainActivity
import com.talwinter.bptracker.R
import com.talwinter.bptracker.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Morning and evening prompts.
 *
 * Without these the 722 window quietly goes unfinished — the grid shows what you owe, but
 * only if you open the app, and the whole point is that you often won't.
 *
 * ## Why exact alarms
 *
 * This originally used setInexactRepeating with INTERVAL_DAY, on the reasoning that a
 * health habit is not an appointment and batching costs nothing. That was wrong, and
 * measurably so: an alarm set for 15:51 was still sitting undelivered in the queue minutes
 * later, with the app in the ACTIVE standby bucket and notifications granted. Android
 * batches long-interval inexact alarms into windows chosen for power, not for the minute
 * you asked for, so "roughly on time" can mean "not today".
 *
 * A reminder that might not arrive is not a reminder, so this now uses exact alarms.
 *
 * USE_EXACT_ALARM (API 33+) is granted at install with no prompt, which suits an app whose
 * user-facing job is to fire at a chosen time. On older releases, or if exact scheduling is
 * somehow unavailable, it falls back to a tight setWindow rather than failing silently.
 *
 * Exact alarms cannot repeat, so each firing re-arms the next one — and BootReceiver
 * re-arms both after a restart, since alarms do not survive reboot.
 */
object Reminders {

    /**
     * Versioned on purpose. A channel's importance is fixed once created — only the user
     * can change it afterwards — so raising IMPORTANCE_DEFAULT to HIGH in code does
     * nothing on an install where the old channel already exists. Bumping the id is the
     * only way to ship the change; the previous channel is deleted so it does not sit in
     * the system settings list forever.
     */
    const val CHANNEL_ID = "bp_reminders_v2"
    private const val LEGACY_CHANNEL_ID = "bp_reminders"
    const val EXTRA_SLOT = "slot"
    const val MORNING = "morning"
    const val EVENING = "evening"

    private const val REQUEST_MORNING = 1001
    private const val REQUEST_EVENING = 1002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)

        // HIGH so it appears as a heads-up banner rather than only a status-bar icon.
        // Twice a day, at times the user chose, for something they have to stop and do —
        // a silent icon is too easy to scroll past, and a missed session leaves a hole in
        // the 7-day window that cannot be filled in later.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reading reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Morning and evening prompts to take a blood pressure reading."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun schedule(context: Context, morning: LocalTime, evening: LocalTime) {
        ensureChannel(context)
        armNext(context, morning, REQUEST_MORNING, MORNING)
        armNext(context, evening, REQUEST_EVENING, EVENING)
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pendingIntent(context, REQUEST_MORNING, MORNING))
        alarms.cancel(pendingIntent(context, REQUEST_EVENING, EVENING))
    }

    /** Re-arms one slot for its next occurrence. Called after a firing, and at boot. */
    fun armNext(context: Context, time: LocalTime, requestCode: Int, slot: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val zone = ZoneId.systemDefault()

        val now = LocalDateTime.now(zone)
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(zone).toInstant().toEpochMilli()

        val intent = pendingIntent(context, requestCode, slot)
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarms.canScheduleExactAlarms()

        if (canBeExact) {
            // AllowWhileIdle so Doze cannot swallow an evening reminder on a quiet phone.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            // Degraded but honest: a ten-minute window instead of an unbounded batch.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, intent)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, slot: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_SLOT, slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun requestCodeFor(slot: String): Int =
        if (slot == MORNING) REQUEST_MORNING else REQUEST_EVENING
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(Reminders.EXTRA_SLOT) ?: Reminders.MORNING
        Log.i(TAG, "onReceive slot=$slot")
        Reminders.ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (slot == Reminders.MORNING) "Morning reading" else "Evening reading")
            .setContentText(
                if (slot == Reminders.MORNING)
                    "Before medication and breakfast. Sit 5 minutes first, then two readings."
                else
                    "Two readings, a minute or two apart."
            )
            // Priority matters on API < 26, where channels do not exist; the channel
            // importance governs modern releases. Both say the same thing.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // POST_NOTIFICATIONS may have been revoked since scheduling; dropping the nudge is
        // the correct outcome, not a crash. It is logged, though — swallowing this silently
        // made a real failure undiagnosable.
        val manager = NotificationManagerCompat.from(context)
        Log.i(TAG, "notificationsEnabled=${manager.areNotificationsEnabled()}")
        runCatching {
            manager.notify(if (slot == Reminders.MORNING) 1 else 2, notification)
            Log.i(TAG, "notify posted for slot=$slot on channel=${Reminders.CHANNEL_ID}")
        }.onFailure { Log.e(TAG, "notify failed", it) }

        // Exact alarms are one-shot, so tomorrow's has to be booked now. Without this the
        // reminder fires exactly once and then silently stops.
        val pending = goAsync()
        try {
            val settings = SettingsStore(context)
            runBlocking {
                if (!settings.remindersEnabled.first()) return@runBlocking
                val minute =
                    if (slot == Reminders.MORNING) settings.morningMinute.first()
                    else settings.eveningMinute.first()
                Reminders.armNext(
                    context,
                    LocalTime.ofSecondOfDay(minute * 60L),
                    Reminders.requestCodeFor(slot),
                    slot
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "re-arm failed", e)
        } finally {
            pending.finish()
        }
    }

    private companion object {
        const val TAG = "BpReminder"
    }
}
