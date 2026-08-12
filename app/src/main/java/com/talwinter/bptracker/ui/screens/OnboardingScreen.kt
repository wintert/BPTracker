package com.talwinter.bptracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talwinter.bptracker.clinical.Guideline

/**
 * First run asks which standard to classify against, rather than silently defaulting.
 * The two guidelines disagree on category boundaries, so guessing wrong would mislabel
 * every reading the user ever takes — see CLINICAL-REFERENCE.md §2b.
 */
@Composable
fun OnboardingScreen(onDone: (Guideline) -> Unit) {
    var selected by remember { mutableStateOf(Guideline.ESC_2024) }

    Column(
        Modifier
            .fillMaxSize()
            // This screen has no Scaffold, so nothing insets it. Without this the
            // disclaimer at the bottom sits under the gesture bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Blood pressure", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "One thing to set up first.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        Text("Which standard should readings be judged against?", fontWeight = FontWeight.SemiBold)
        Text(
            "The two disagree about where the categories begin, so the same reading can be " +
                "labelled differently. You can change this later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GuidelineOption(
            guideline = Guideline.ESC_2024,
            detail = "European. Three bands: non-elevated, elevated, hypertension. Common in Israel and Europe.",
            selected = selected == Guideline.ESC_2024,
            onSelect = { selected = Guideline.ESC_2024 }
        )
        GuidelineOption(
            guideline = Guideline.ACC_AHA_2025,
            detail = "American. Four bands: normal, elevated, stage 1, stage 2.",
            selected = selected == Guideline.ACC_AHA_2025,
            onSelect = { selected = Guideline.ACC_AHA_2025 }
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("Readings are treated as home measurements", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Home readings run lower than clinic ones, so hypertension starts at 135/85 here " +
                        "rather than 140/90. That is deliberate and matches both guidelines.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { onDone(selected) }, Modifier.fillMaxWidth()) { Text("Start") }

        Text(
            "This app records and summarises your own measurements. It is not a medical device " +
                "and does not diagnose anything.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GuidelineOption(
    guideline: Guideline,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Text(guideline.shortName, fontWeight = FontWeight.SemiBold)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp))
        }
    }
}
