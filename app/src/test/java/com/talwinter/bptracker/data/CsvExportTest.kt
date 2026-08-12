package com.talwinter.bptracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Export is the promise that this data is never locked in — it is what gets taken to a
 * doctor's appointment. A CSV that a spreadsheet misparses breaks that quietly.
 */
class CsvExportTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")

    private fun reading(
        systolic: Int = 128,
        diastolic: Int = 82,
        pulse: Int? = 70,
        notes: String? = null,
        hour: Int = 8,
        day: Int = 3
    ) = Reading(
        timestamp = LocalDateTime.of(2026, 8, day, hour, 30)
            .atZone(zone).toInstant().toEpochMilli(),
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse,
        notes = notes
    )

    private fun rows(csv: String) = csv.trim().lines()

    @Test
    fun `header comes first and matches the column count`() {
        val csv = CsvExport.render(listOf(reading()), zone)
        val lines = rows(csv)
        assertEquals(CsvExport.HEADER, lines[0])
        assertEquals(
            "row has a different number of columns than the header",
            lines[0].split(",").size,
            lines[1].split(",").size
        )
    }

    @Test
    fun `rows are ordered oldest first regardless of input order`() {
        val csv = CsvExport.render(
            listOf(reading(day = 5, systolic = 150), reading(day = 1, systolic = 110)),
            zone
        )
        val lines = rows(csv)
        assertTrue(lines[1].contains("110"))
        assertTrue(lines[2].contains("150"))
    }

    @Test
    fun `notes containing commas do not break the column layout`() {
        val csv = CsvExport.render(listOf(reading(notes = "stressed, slept badly")), zone)
        val line = rows(csv)[1]
        assertTrue(line.contains("\"stressed, slept badly\""))
        // Quoted correctly, so the comma inside must not add a column.
        assertEquals(CsvExport.HEADER.split(",").size, splitCsv(line).size)
    }

    @Test
    fun `quotes inside notes are doubled per RFC 4180`() {
        val csv = CsvExport.render(listOf(reading(notes = "felt \"off\" all day")), zone)
        assertTrue(rows(csv)[1].contains("\"felt \"\"off\"\" all day\""))
    }

    @Test
    fun `a missing pulse becomes an empty cell not a zero`() {
        // A zero would be read as a real measurement of 0 bpm by anything consuming this.
        val line = rows(CsvExport.render(listOf(reading(pulse = null)), zone))[1]
        val cells = splitCsv(line)
        assertEquals("", cells[3])
    }

    @Test
    fun `derived values are included so the export stands alone`() {
        val line = rows(CsvExport.render(listOf(reading(systolic = 153, diastolic = 84)), zone))[1]
        val cells = splitCsv(line)
        assertEquals("69", cells[4])    // pulse pressure
        assertEquals("107", cells[5])   // MAP
    }

    @Test
    fun `timestamps are ISO local time in the given zone`() {
        val line = rows(CsvExport.render(listOf(reading(hour = 8, day = 3)), zone))[1]
        assertTrue(line.startsWith("2026-08-03T08:30"))
    }

    @Test
    fun `an empty export is still a valid file with a header`() {
        assertEquals(CsvExport.HEADER, rows(CsvExport.render(emptyList(), zone))[0])
    }

    /** Minimal RFC 4180 splitter, so tests verify real parseability rather than string shape. */
    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }
}
