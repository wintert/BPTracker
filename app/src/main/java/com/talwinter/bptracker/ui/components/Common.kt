package com.talwinter.bptracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talwinter.bptracker.clinical.BpCategory
import com.talwinter.bptracker.clinical.Clinical
import com.talwinter.bptracker.clinical.Guideline
import com.talwinter.bptracker.data.Reading
import com.talwinter.bptracker.ui.theme.Palette

/**
 * Category label. Always shows the guideline it was judged against — the same numbers
 * mean different things under ESC and ACC/AHA, so an unlabelled badge is ambiguous.
 */
@Composable
fun CategoryBadge(
    category: BpCategory,
    guideline: Guideline?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier
            .background(Palette.signalWash(category, dark), RoundedCornerShape(6.dp))
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp)
    ) {
        Text(
            // The label always carries the text, never colour alone — a red/green
            // distinction is exactly what a colour-blind reader cannot use.
            text = if (compact || guideline == null) category.displayName
            else "${category.displayName} · ${guideline.shortName}",
            color = Palette.signal(category, dark),
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Hypertensive crisis dialog. Deliberately not dismissible by tapping outside — this is
 * the one alert in the app that must not be swiped away by accident.
 *
 * Per AHA: wait 5 minutes and re-measure. Emergency services if there are symptoms.
 */
@Composable
fun CrisisDialog(
    systolic: Int,
    diastolic: Int,
    onRemeasure: () -> Unit,
    onSaveAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* must be an explicit choice */ },
        title = { Text("That reading is very high", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "$systolic/$diastolic is at or above ${Clinical.CRISIS_SYSTOLIC}/${Clinical.CRISIS_DIASTOLIC}, " +
                        "which counts as a hypertensive crisis.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text("Sit quietly for 5 minutes and measure again.", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "If it is still this high AND you have chest pain, shortness of breath, weakness or " +
                        "numbness, trouble speaking, vision changes, or back pain — call emergency services now " +
                        "(101 in Israel).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "If it is still this high with no symptoms, contact your doctor promptly.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onRemeasure) { Text("I'll measure again") } },
        dismissButton = { TextButton(onClick = onSaveAnyway) { Text("Save this reading") } }
    )
}
