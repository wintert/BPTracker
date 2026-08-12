package com.talwinter.bptracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talwinter.bptracker.clinical.Clinical
import com.talwinter.bptracker.data.Occasion
import com.talwinter.bptracker.data.Reading
import com.talwinter.bptracker.data.ReadingSource
import com.talwinter.bptracker.ui.HomeState
import com.talwinter.bptracker.ui.components.CategoryBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: HomeState, onEdit: (Long) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        if (state.readings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No readings yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.readings, key = { it.id }) { reading ->
                HistoryRow(reading, state) { onEdit(reading.id) }
            }
        }
    }
}

@Composable
private fun HistoryRow(reading: Reading, state: HomeState, onClick: () -> Unit) {
    val category = runCatching {
        Clinical.classify(reading.systolic, reading.diastolic, state.guideline, state.setting)
    }.getOrNull()

    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${reading.systolic}/${reading.diastolic}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    reading.pulse?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("$it bpm", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                category?.let { CategoryBadge(it, null, compact = true) }
            }

            Spacer(Modifier.height(6.dp))
            Text(formatWhen(reading.timestamp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            val tags = buildList {
                if (reading.occasion != Occasion.OTHER) add(reading.occasion.name.lowercase())
                add(reading.arm.name.lowercase() + " arm")
                if (reading.irregularHeartbeat) add("irregular beat")
                if (reading.excludeFromAverages) add("excluded from average")
                if (reading.source != ReadingSource.MANUAL) {
                    add("from photo" + (reading.extractionConfidence?.let { " ${(it * 100).toInt()}%" } ?: ""))
                }
                if (reading.wasEditedAfterExtraction) add("edited")
            }
            if (tags.isNotEmpty()) {
                Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            reading.notes?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
