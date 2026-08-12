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

/**
 * Systolic and diastolic over time, drawn directly rather than pulled from a chart
 * library — two lines and a threshold band do not justify the dependency.
 */
@Composable
fun TrendChart(
    readings: List<Reading>,
    guideline: Guideline,
    modifier: Modifier = Modifier
) {
    if (readings.size < 2) {
        Box(modifier.height(180.dp), contentAlignment = Alignment.Center) {
            Text(
                "Two readings will start the chart.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val ordered = readings.sortedBy { it.timestamp }
    val systolicColor = MaterialTheme.colorScheme.primary
    val diastolicColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    // Fixed, generous scale so the shape of the trend is comparable between visits
    // rather than rescaling dramatically every time a new reading lands.
    val minValue = minOf(50, ordered.minOf { it.diastolic } - 10)
    val maxValue = maxOf(160, ordered.maxOf { it.systolic } + 10)
    val span = (maxValue - minValue).toFloat()

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            fun y(value: Int) = size.height * (1f - (value - minValue) / span)
            fun x(index: Int) = size.width * index / (ordered.size - 1).toFloat()

            // The home hypertension threshold, so the trend is read against something.
            val thresholdY = y(135)
            drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, thresholdY),
                androidx.compose.ui.geometry.Offset(size.width, thresholdY), strokeWidth = 1.5f)

            fun line(values: List<Int>, color: Color) {
                val path = Path().apply {
                    values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
                }
                drawPath(path, color, style = Stroke(width = 3.5f))
            }
            line(ordered.map { it.systolic }, systolicColor)
            line(ordered.map { it.diastolic }, diastolicColor)
        }
        Spacer(Modifier.height(6.dp))
        Row {
            LegendDot("Systolic", systolicColor)
            Spacer(Modifier.padding(horizontal = 8.dp))
            LegendDot("Diastolic", diastolicColor)
            Spacer(Modifier.padding(horizontal = 8.dp))
            Text(
                "line at 135",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.background(color, RoundedCornerShape(50)).padding(5.dp))
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
