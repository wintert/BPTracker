package com.talwinter.bptracker.data

import kotlinx.coroutines.flow.Flow

class ReadingRepository(private val dao: ReadingDao) {
    fun observeAll(): Flow<List<Reading>> = dao.observeAll()
    fun observeLatest(): Flow<Reading?> = dao.observeLatest()
    fun observeCount(): Flow<Int> = dao.observeCount()
    fun observeSince(since: Long): Flow<List<Reading>> = dao.observeSince(since)

    suspend fun byId(id: Long): Reading? = dao.byId(id)
    suspend fun add(reading: Reading): Long = dao.insert(reading)
    suspend fun update(reading: Reading) = dao.update(reading)
    suspend fun delete(reading: Reading) = dao.delete(reading)

    /** CSV export — the escape hatch that keeps this data yours and not locked in. */
    suspend fun exportCsv(readings: List<Reading>): String = buildString {
        appendLine("timestamp_iso,systolic,diastolic,pulse,pulse_pressure,map,arm,position,occasion,medication,irregular_heartbeat,excluded_from_averages,source,confidence,edited,notes")
        val fmt = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
        readings.sortedBy { it.timestamp }.forEach { r ->
            val iso = java.time.Instant.ofEpochMilli(r.timestamp)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(fmt)
            appendLine(
                listOf(
                    iso, r.systolic, r.diastolic, r.pulse ?: "", r.pulsePressure, r.meanArterialPressure,
                    r.arm, r.position, r.occasion, r.medicationState, r.irregularHeartbeat,
                    r.excludeFromAverages, r.source, r.extractionConfidence ?: "",
                    r.wasEditedAfterExtraction, csvEscape(r.notes)
                ).joinToString(",")
            )
        }
    }

    private fun csvEscape(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
