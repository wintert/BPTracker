package com.talwinter.bptracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.talwinter.bptracker.ui.theme.Space
import com.talwinter.bptracker.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Editable date and time for when a reading was actually taken.
 *
 * This is not a nicety. The 722 assessment buckets readings by calendar day and by
 * morning/evening, so a reading entered at the wrong time lands in the wrong cell of the
 * grid and skews the week's average. Anyone catching up on a reading they took earlier —
 * which is most people, most of the time — needs to correct it.
 *
 * Future dates are blocked: a reading cannot have been taken tomorrow, and a stray typo
 * would otherwise sit outside the current window and silently never count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenTakenRow(
    timestamp: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val local = remember(timestamp) {
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDateTime()
    }

    Column(modifier) {
        Text("WHEN IT WAS TAKEN", style = Type.Eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1.4f)) {
                Text(local.format(DATE_FORMAT), style = Type.Body)
            }
            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                Text(local.format(TIME_FORMAT), style = Type.ReadingSmall)
            }
        }
        if (local.toLocalDate() != LocalDate.now(zone)) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "Logging a reading from ${local.format(DATE_FORMAT)}.",
                style = Type.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDate) {
        val today = LocalDate.now(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = local.toLocalDate()
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC"))
                        .toLocalDate().isAfter(today)
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // The picker works in UTC; keep the user's chosen wall-clock time
                        // and only swap the calendar date, so a date change never shifts
                        // the reading by a timezone offset.
                        val pickedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC")).toLocalDate()
                        onChange(
                            LocalDateTime.of(pickedDate, local.toLocalTime())
                                .atZone(zone).toInstant().toEpochMilli()
                        )
                    }
                    showDate = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(
            initialHour = local.hour,
            initialMinute = local.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Time taken") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        local.withHour(state.hour).withMinute(state.minute).withSecond(0)
                            .atZone(zone).toInstant().toEpochMilli()
                    )
                    showTime = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } }
        )
    }
}

/** Confirmation before destroying a reading, and its photo along with it. */
@Composable
fun DeleteReadingDialog(
    systolic: Int,
    diastolic: Int,
    hasPhoto: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this reading?") },
        text = {
            Text(
                buildString {
                    append("$systolic/$diastolic will be removed from your history and from every average.")
                    if (hasPhoto) append(" Its photo will be deleted too.")
                    append("\n\nThis can't be undone.")
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } }
    )
}

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
