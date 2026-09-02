package com.talwinter.bptracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.talwinter.bptracker.clinical.Analysis
import com.talwinter.bptracker.clinical.BpCategory
import com.talwinter.bptracker.clinical.Clinical
import com.talwinter.bptracker.clinical.Protocol722
import com.talwinter.bptracker.data.Arm
import com.talwinter.bptracker.data.Reading
import com.talwinter.bptracker.ui.HomeState
import com.talwinter.bptracker.ui.components.CategoryBadge
import com.talwinter.bptracker.ui.components.ProtocolGrid
import com.talwinter.bptracker.ui.components.TrendChart
import com.talwinter.bptracker.ui.theme.Palette
import com.talwinter.bptracker.ui.theme.Space
import com.talwinter.bptracker.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    onAdd: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blood pressure", style = Type.Title) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, "Settings") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add reading") }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            Spacer(Modifier.height(Space.xs))

            // The 7-day average leads, not the latest reading. That inversion is the whole
            // argument of the app: one reading is noise, the protocol average is signal.
            state.assessment?.let { SevenDayHero(it, state) }

            state.readings.firstOrNull()?.let { LatestStrip(it, state) }

            if (state.readings.size >= 2) {
                SectionCard("TREND") {
                    TrendChart(state.readings.take(30).reversed(), state.guideline)
                }
            }

            ArmCard(state)

            if (state.readings.isEmpty()) EmptyState()

            OutlinedButton(onClick = onHistory, Modifier.fillMaxWidth()) {
                Text(if (state.readings.isEmpty()) "History" else "All ${state.readings.size} readings")
            }

            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun SevenDayHero(assessment: Protocol722.Assessment, state: HomeState) {
    val dark = isSystemInDarkTheme()
    SectionCard("7-DAY AVERAGE") {
        val overall = assessment.overall
        if (overall != null && assessment.hasEnoughForResult) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${overall.systolic}/${overall.diastolic}", style = Type.Reading)
                Spacer(Modifier.width(Space.sm))
                Text("mmHg", style = Type.Unit, modifier = Modifier.padding(bottom = 9.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            overall.pulse?.let {
                Text("$it bpm resting", style = Type.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Space.md))
            assessment.category?.let { CategoryBadge(it, assessment.guideline) }
        } else {
            // Plain prose, not the big monospace readout. That style is reserved for
            // actual measurements — using it for a status sentence made "0 of 3 days"
            // read like a blood pressure value.
            Text("Not enough readings yet", style = Type.Heading)
            Spacer(Modifier.height(Space.xs))
            Text(
                "${assessment.daysWithData} of ${Protocol722.MINIMUM_DAYS_FOR_RESULT} days so far. " +
                    "An average needs at least three days behind it to mean anything.",
                style = Type.Body,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Space.lg))
        ProtocolGrid(
            readings = state.readings,
            windowStart = LocalDate.now().minusDays((Protocol722.WINDOW_DAYS - 1).toLong())
        )

        Spacer(Modifier.height(Space.md))
        Text(
            "${assessment.readingsUsed} of ${Protocol722.EXPECTED_READINGS} readings counted",
            style = Type.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (assessment.readingsExcludedAsNonQualifying > 0) {
            Text(
                "${assessment.readingsExcludedAsNonQualifying} left out: stored averages, or not a " +
                    "morning/evening session.",
                style = Type.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        assessment.morningEveningSystolicDelta?.let { delta ->
            if (kotlin.math.abs(delta) > 10) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    if (delta > 0) "Mornings run $delta mmHg higher than evenings."
                    else "Evenings run ${-delta} mmHg higher than mornings.",
                    style = Type.Small,
                    color = Palette.signal(
                        assessment.category ?: com.talwinter.bptracker.clinical.BpCategory.ELEVATED, dark
                    )
                )
            }
        }
    }
}

@Composable
private fun LatestStrip(reading: Reading, state: HomeState) {
    val category = runCatching {
        Clinical.classify(reading.systolic, reading.diastolic, state.guideline, state.setting)
    }.getOrNull()

    SectionCard("LATEST") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${reading.systolic}/${reading.diastolic}", style = Type.ReadingMedium)
            reading.pulse?.let {
                Spacer(Modifier.width(Space.sm))
                Text("$it bpm", style = Type.Small,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            category?.let { CategoryBadge(it, null, compact = true) }
        }
        Spacer(Modifier.height(Space.xs))
        Text(formatWhen(reading.timestamp), style = Type.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.sm))
        Text(
            "One reading doesn't diagnose anything.",
            style = Type.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Only appears once both arms have enough readings to mean anything. Someone sensibly
 * using one arm consistently never sees this card, which is correct — a difference
 * computed from one or two readings on the other arm would be noise dressed as a finding.
 */
@Composable
private fun ArmCard(state: HomeState) {
    val comparison = Analysis.compareArms(state.readings) ?: return
    val dark = isSystemInDarkTheme()
    val higher = if (comparison.higherArm == Arm.LEFT) "Left" else "Right"

    SectionCard("LEFT VERSUS RIGHT ARM") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${comparison.systolicDifference}", style = Type.ReadingMedium)
            Spacer(Modifier.width(Space.sm))
            Text("mmHg apart", style = Type.Small, modifier = Modifier.padding(bottom = 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "Left ${comparison.leftSystolic}/${comparison.leftDiastolic} " +
                "(${comparison.leftCount} readings)   ·   " +
                "Right ${comparison.rightSystolic}/${comparison.rightDiastolic} " +
                "(${comparison.rightCount})",
            style = Type.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (comparison.isNotable) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "$higher arm reads higher, by enough to be worth mentioning to your doctor. " +
                    "In the meantime, take your readings on the ${higher.lowercase()} arm — using the " +
                    "lower one would understate your pressure.",
                style = Type.Small,
                color = Palette.signal(BpCategory.ELEVATED, dark)
            )
        } else {
            Spacer(Modifier.height(Space.sm))
            Text(
                "Close enough that either arm is fine. Stay consistent with whichever you use.",
                style = Type.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState() {
    SectionCard("START HERE") {
        Text("Nothing logged yet", style = Type.Heading)
        Spacer(Modifier.height(Space.sm))
        Text(
            "Measure with your cuff, then add the reading — type the numbers, or photograph " +
                "the display and let it read them.",
            style = Type.Body
        )
        Spacer(Modifier.height(Space.md))
        Text(
            "Aim for two readings each morning and evening for a week. The grid above fills " +
                "as you go.",
            style = Type.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Every block uses the same frame: a tracked uppercase eyebrow over hairline-bordered
 * content, no shadows. Elevation would imply a stack of floating things; this is meant to
 * read as one continuous instrument panel.
 */
@Composable
private fun SectionCard(eyebrow: String, content: @Composable ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (dark) Palette.SurfaceDark else Palette.Surface)
            .padding(Space.lg)
    ) {
        Text(
            eyebrow,
            style = Type.Eyebrow,
            color = if (dark) Palette.SlateDark else Palette.Slate
        )
        Spacer(Modifier.height(Space.sm))
        content()
    }
}

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")

fun formatWhen(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
