package com.talwinter.bptracker.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.talwinter.bptracker.ui.BpViewModel
import com.talwinter.bptracker.ui.theme.Space
import com.talwinter.bptracker.ui.theme.Type
import java.util.Locale

/**
 * Reminder controls.
 *
 * The 722 protocol needs a morning and an evening reading for seven days. The grid on the
 * home screen shows what is still owed, but only to someone who opens the app — so without
 * a nudge the week quietly goes unfinished. These two prompts are what make the protocol
 * completable rather than aspirational.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSection(vm: BpViewModel) {
    val context = LocalContext.current
    val state by vm.reminders.collectAsState()

    var showPicker by remember { mutableStateOf<String?>(null) }   // "morning" | "evening" | null
    var permissionDenied by remember { mutableStateOf(false) }

    fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            vm.applyReminders(true, state.morningMinute, state.eveningMinute)
        } else {
            // Turning the switch on while notifications are blocked would be a lie —
            // alarms would fire and nothing would appear. Leave it off and say why.
            permissionDenied = true
            vm.applyReminders(false, state.morningMinute, state.eveningMinute)
        }
    }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Remind me twice a day", style = Type.Body)
            Switch(
                checked = state.enabled,
                onCheckedChange = { wantOn ->
                    if (!wantOn) {
                        permissionDenied = false
                        vm.applyReminders(false, state.morningMinute, state.eveningMinute)
                    } else if (notificationsAllowed()) {
                        permissionDenied = false
                        vm.applyReminders(true, state.morningMinute, state.eveningMinute)
                    } else {
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        Spacer(Modifier.height(Space.xs))
        Text(
            "A morning and an evening prompt, which is what the 7-day protocol asks for.",
            style = Type.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (permissionDenied) {
            Spacer(Modifier.height(Space.sm))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(Space.md)) {
                    Text(
                        "Notifications are turned off for this app, so reminders can't appear.",
                        style = Type.Small,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(Space.sm))
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }) { Text("Open notification settings") }
                }
            }
        }

        if (state.enabled) {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                TimeButton("Morning", state.morningMinute, Modifier.weight(1f)) { showPicker = "morning" }
                TimeButton("Evening", state.eveningMinute, Modifier.weight(1f)) { showPicker = "evening" }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                "Take the morning reading before medication and breakfast — that's the one the " +
                    "protocol is specific about.",
                style = Type.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    showPicker?.let { which ->
        val current = if (which == "morning") state.morningMinute else state.eveningMinute
        val pickerState = rememberTimePickerState(
            initialHour = current / 60,
            initialMinute = current % 60,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = null },
            title = { Text(if (which == "morning") "Morning reminder" else "Evening reminder") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = pickerState.hour * 60 + pickerState.minute
                    vm.applyReminders(
                        enabled = true,
                        morningMinute = if (which == "morning") minutes else state.morningMinute,
                        eveningMinute = if (which == "evening") minutes else state.eveningMinute
                    )
                    showPicker = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showPicker = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TimeButton(label: String, minuteOfDay: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(Locale.ROOT), style = Type.Eyebrow)
            Text(
                String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60),
                style = Type.ReadingSmall
            )
        }
    }
}
