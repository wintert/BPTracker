package com.talwinter.bptracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.talwinter.bptracker.clinical.Analysis
import com.talwinter.bptracker.clinical.BpCategory
import com.talwinter.bptracker.clinical.Guideline
import com.talwinter.bptracker.data.Reading
import com.talwinter.bptracker.ui.theme.Palette
import com.talwinter.bptracker.ui.theme.Space
import com.talwinter.bptracker.ui.theme.Type

/**
 * Systolic and diastolic over time.
 *
 * Two deliberate choices, both aimed at the same problem — a bare polyline of home
 * readings is mostly noise, and noise invites over-reading a single bad morning:
 *
 *  - Faint category bands behind the plot, so a point's height means something without
 *    the reader doing arithmetic against a remembered threshold.
 *  - A heavy rolling average over the thin raw series, so the trend is what the eye lands
 *    on and individual spikes read as what they are.
 *
 * Drawn directly rather than through a chart library: two series, a rolling mean and
 * three bands do not justify the dependency, and the library would fight the palette.
 */
@Composable
fun TrendChart(
    readings: List<Reading>,
    guideline: Guideline,
    modifier: Modifier = Modifier
) {
    if (readings.size < 2) {
        Box(modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text(
                "Two readings will start the chart.",
                style = Type.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val dark = isSystemInDarkTheme()
    val ordered = readings.sortedBy { it.timestamp }
    val rolled = Analysis.rollingAverage(ordered)

    val rawColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val systolicColour = Palette.signal(BpCategory.STAGE_2, dark)
    val diastolicColour = Palette.signal(BpCategory.ELEVATED, dark)
    val gridColour = if (dark) Palette.HairlineDark else Palette.Hairline

    // Fixed floor and ceiling so the shape stays comparable week to week instead of
    // rescaling every time a new extreme lands.
    val minValue = minOf(60, ordered.minOf { it.diastolic } - 8)
    val maxValue = maxOf(165, ordered.maxOf { it.systolic } + 8)
    val span = (maxValue - minValue).toFloat()

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            fun y(v: Float) = size.height * (1f - (v - minValue) / span)
            fun x(i: Int) = size.width * i / (ordered.size - 1).toFloat()

            // Category bands, systolic. Faint enough to sit behind the data.
            fun band(from: Int, to: Int, category: BpCategory) {
                val top = y(to.toFloat())
                val bottom = y(from.toFloat())
                drawRect(
                    color = Palette.signal(category, dark).copy(alpha = if (dark) 0.10f else 0.07f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, (bottom - top).coerceAtLeast(0f))
                )
            }
            when (guideline) {
                Guideline.ESC_2024 -> {
                    band(minValue, 120, BpCategory.NON_ELEVATED)
                    band(120, 135, BpCategory.ELEVATED)
                    band(135, maxValue, BpCategory.HYPERTENSION)
                }
                Guideline.ACC_AHA_2025 -> {
                    band(minValue, 120, BpCategory.NORMAL)
                    band(120, 130, BpCategory.ELEVATED)
                    band(130, 135, BpCategory.STAGE_1)
                    band(135, maxValue, BpCategory.STAGE_2)
                }
            }

            // Threshold lines, dashed so they read as reference rather than data.
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            listOf(135, 85).forEach { threshold ->
                val ty = y(threshold.toFloat())
                drawLine(gridColour, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 1.4f, pathEffect = dash)
            }

            fun polyline(values: List<Float>, colour: Color, width: Float) {
                val path = Path().apply {
                    values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
                }
                drawPath(path, colour, style = Stroke(width = width))
            }

            // Raw readings first, underneath.
            polyline(ordered.map { it.systolic.toFloat() }, rawColour, 1.4f)
            polyline(ordered.map { it.diastolic.toFloat() }, rawColour, 1.4f)
            ordered.forEachIndexed { i, r ->
                drawCircle(rawColour, 2f, Offset(x(i), y(r.systolic.toFloat())))
                drawCircle(rawColour, 2f, Offset(x(i), y(r.diastolic.toFloat())))
            }

            // Rolling average on top — the line the eye should follow.
            polyline(rolled.map { it.first }, systolicColour, 3.5f)
            polyline(rolled.map { it.second }, diastolicColour, 3.5f)
        }

        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend("Systolic", systolicColour)
            Spacer(Modifier.width(Space.md))
            Legend("Diastolic", diastolicColour)
            Spacer(Modifier.width(Space.md))
            Text(
                "faint = each reading",
                style = Type.Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Legend(label: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(14.dp).height(3.dp)) {
            drawLine(colour, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3.5f)
        }
        Spacer(Modifier.width(5.dp))
        Text(label, style = Type.Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
