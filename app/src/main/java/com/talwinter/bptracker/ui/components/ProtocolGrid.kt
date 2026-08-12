package com.talwinter.bptracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.talwinter.bptracker.clinical.Protocol722
import com.talwinter.bptracker.data.Occasion
import com.talwinter.bptracker.data.Reading
import com.talwinter.bptracker.ui.theme.Palette
import com.talwinter.bptracker.ui.theme.Space
import com.talwinter.bptracker.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The 722 protocol, drawn as the lattice it actually is: 7 day-columns, a morning row and
 * an evening row, two dots per cell for the two readings a session calls for.
 *
 * This is the app's one piece of real invention, and it earns its place by encoding the
 * clinical method rather than decorating it. Day 1's column is struck through because the
 * protocol genuinely discards it — a rule most people find surprising, and one this makes
 * self-evident without a paragraph of explanation. Progress, adherence, and which session
 * you still owe are all readable in a single glance at something two centimetres tall.
 */
@Composable
fun ProtocolGrid(
    readings: List<Reading>,
    windowStart: LocalDate,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val dark = isSystemInDarkTheme()
    val accent = if (dark) Palette.CalmDark else Palette.Calm
    val empty = if (dark) Palette.HairlineDark else Palette.Hairline
    val muted = if (dark) Palette.SlateDark else Palette.Slate

    // counts[day][occasion] -> how many qualifying readings landed there, capped at 2.
    val counts = remember(readings, windowStart) {
        Array(Protocol722.WINDOW_DAYS) { IntArray(2) }.also { grid ->
            readings.forEach { r ->
                if (r.excludeFromAverages) return@forEach
                val date = Instant.ofEpochMilli(r.timestamp).atZone(zone).toLocalDate()
                val dayIndex = (date.toEpochDay() - windowStart.toEpochDay()).toInt()
                if (dayIndex !in 0 until Protocol722.WINDOW_DAYS) return@forEach
                val occ = when (r.occasion) {
                    Occasion.MORNING -> 0
                    Occasion.EVENING -> 1
                    Occasion.OTHER -> return@forEach
                }
                if (grid[dayIndex][occ] < Protocol722.READINGS_PER_OCCASION) grid[dayIndex][occ]++
            }
        }
    }

    val logged = counts.drop(1).sumOf { it.sum() }
    val progress by animateFloatAsState(
        targetValue = logged / Protocol722.EXPECTED_READINGS.toFloat(),
        animationSpec = tween(500),
        label = "protocolProgress"
    )

    Column(
        modifier.semantics {
            contentDescription =
                "Seven day protocol. $logged of ${Protocol722.EXPECTED_READINGS} readings logged. " +
                    "Day one is discarded by design."
        }
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(34.dp))
            repeat(Protocol722.WINDOW_DAYS) { day ->
                androidx.compose.material3.Text(
                    text = if (day == 0) "1" else "${day + 1}",
                    style = Type.Eyebrow,
                    color = if (day == 0) muted.copy(alpha = 0.5f) else muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(Space.xs))

        listOf("AM" to 0, "PM" to 1).forEach { (label, occIndex) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    text = label,
                    style = Type.Eyebrow,
                    color = muted,
                    modifier = Modifier.width(34.dp)
                )
                repeat(Protocol722.WINDOW_DAYS) { day ->
                    val isDiscardedDay = day == 0
                    Canvas(Modifier.weight(1f).height(26.dp)) {
                        val count = counts[day][occIndex]
                        val radius = size.height * 0.16f
                        val gap = radius * 2.6f
                        val cx = size.width / 2f
                        val cy = size.height / 2f

                        listOf(cx - gap / 2, cx + gap / 2).forEachIndexed { i, x ->
                            val isFilled = count > i
                            val colour = when {
                                isDiscardedDay && isFilled -> muted.copy(alpha = 0.45f)
                                isFilled -> accent
                                else -> empty
                            }
                            if (isFilled) drawCircle(colour, radius, Offset(x, cy))
                            else drawCircle(colour, radius, Offset(x, cy), style = Stroke(width = 1.6f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.xs))
        }

        // The strike-through over day 1: the protocol's own rule, made visible.
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(34.dp))
            Canvas(Modifier.weight(1f).height(12.dp)) {
                drawLine(
                    muted.copy(alpha = 0.55f),
                    Offset(size.width * 0.16f, size.height / 2),
                    Offset(size.width * 0.84f, size.height / 2),
                    strokeWidth = 1.4f
                )
            }
            Spacer(Modifier.weight((Protocol722.WINDOW_DAYS - 1).toFloat()))
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(34.dp))
            androidx.compose.material3.Text(
                "day 1 discarded",
                style = Type.Eyebrow.copy(fontSize = androidx.compose.ui.unit.TextUnit(9.5f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = muted.copy(alpha = 0.75f),
                modifier = Modifier.weight(2.6f)
            )
            Spacer(Modifier.weight(4.4f))
        }
    }
}
