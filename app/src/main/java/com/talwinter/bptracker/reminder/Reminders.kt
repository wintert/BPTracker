package com.talwinter.bptracker.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.talwinter.bptracker.MainActivity
import com.talwinter.bptracker.R
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Morning and evening prompts.
 *
 * Without these the 722 window quietly goes unfinished — the grid shows what you owe, but
 * only if you open the app, and the whole point is that you often won't. Two nudges a day
 * is the minimum that makes a 7-day protocol actually completable.
 *
 * Deliberately inexact alarms: this is a health habit, not an appointment. Letting Android
 * batch them costs nothing and avoids the exact-alarm permission, which on Android 13+
 * would mean asking the user for something intrusive to no real benefit.
 */
object Reminders {

    const val CHANNEL_ID = "bp_reminders"
    private const val REQUEST_MORNING = 1001
    private const val REQUEST_EVENING = 1002

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reading reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning and evening prompts to take a blood pressure reading."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, morning: LocalTime, evening: LocalTime) {
        ensureChannel(context)
        scheduleOne(context, morning, REQUEST_MORNING, MORNING)
        scheduleOne(context, evening, REQUEST_EVENING, EVENING)
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        listOf(REQUEST_MORNING to MORNING, REQUEST_EVENING to EVENING).forEach { (code, slot) ->
            alarms.cancel(pendingIntent(context, code, slot))
        }
    }

    private fun scheduleOne(context: Context, time: LocalTime, requestCode: Int, slot: String) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)

        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context, requestCode, slot)
        )
    }

    private fun pendingIntent(context: Context, requestCode: Int, slot: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_SLOT, slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    const val EXTRA_SLOT = "slot"
    const val MORNING = "morning"
    const val EVENING = "evening"
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(Reminders.EXTRA_SLOT) ?: Reminders.MORNING
        Reminders.ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (slot == Reminders.MORNING) "Morning reading" else "Evening reading"
            )
            .setContentText(
                if (slot == Reminders.MORNING)
                    "Before medication and breakfast. Sit 5 minutes first, then two readings."
                else
                    "Two readings, a minute or two apart."
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // POST_NOTIFICATIONS may have been revoked since scheduling; dropping the nudge is
        // the correct outcome, not a crash.
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(if (slot == Reminders.MORNING) 1 else 2, notification)
        }
    }
}
