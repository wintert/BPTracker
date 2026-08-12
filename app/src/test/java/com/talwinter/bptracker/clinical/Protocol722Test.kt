package com.talwinter.bptracker.clinical

import com.talwinter.bptracker.data.Occasion
import com.talwinter.bptracker.data.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class Protocol722Test {

    private val zone: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val start: LocalDate = LocalDate.of(2026, 8, 1)

    private fun reading(
        day: Int,
        occasion: Occasion,
        systolic: Int,
        diastolic: Int,
        pulse: Int? = 70,
        hour: Int = if (occasion == Occasion.MORNING) 7 else 20,
        exclude: Boolean = false
    ) = Reading(
        timestamp = start.plusDays(day - 1L)
            .atTime(LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli(),
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse,
        occasion = occasion,
        excludeFromAverages = exclude
    )

    /** A full, compliant week: days 1-7, morning + evening, 2 readings each. */
    private fun fullWeek(systolic: Int = 130, diastolic: Int = 82): List<Reading> =
        (1..7).flatMap { day ->
            listOf(Occasion.MORNING, Occasion.EVENING).flatMap { occ ->
                listOf(
                    reading(day, occ, systolic, diastolic),
                    reading(day, occ, systolic, diastolic, hour = if (occ == Occasion.MORNING) 7 else 20)
                )
            }
        }

    @Test
    fun `day one is discarded entirely`() {
        // Day 1 is wildly high; every other day is normal. The average must ignore day 1.
        val readings = fullWeek(systolic = 120, diastolic = 75).toMutableList()
        readings += reading(1, Occasion.MORNING, 200, 120)
        readings += reading(1, Occasion.EVENING, 200, 120)

        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)

        assertEquals(120, a.overall!!.systolic)
        assertEquals(75, a.overall.diastolic)
        // 4 from the compliant day 1, plus the 2 outliers added above.
        assertEquals(6, a.readingsDiscardedFromFirstDay)
    }

    @Test
    fun `a complete week yields 24 readings and is marked complete`() {
        val a = Protocol722.assess(fullWeek(), start, Guideline.ESC_2024, zone = zone)
        assertEquals(Protocol722.EXPECTED_READINGS, a.readingsUsed)
        assertEquals(24, a.readingsUsed)
        assertEquals(6, a.daysWithData)
        assertTrue(a.isComplete)
        assertEquals(1f, a.progressFraction, 0.001f)
    }

    @Test
    fun `memory recalls are excluded so they cannot double count`() {
        val readings = fullWeek(systolic = 120, diastolic = 75).toMutableList()
        // A stored average read off the monitor's memory display; must not pollute.
        readings += reading(3, Occasion.MORNING, 180, 110, exclude = true)

        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)

        assertEquals(120, a.overall!!.systolic)
        assertEquals(1, a.readingsExcludedAsNonQualifying)
        assertEquals(24, a.readingsUsed)
    }

    @Test
    fun `OTHER occasion readings are kept out of the protocol average`() {
        val readings = fullWeek(systolic = 120, diastolic = 75).toMutableList()
        readings += reading(4, Occasion.OTHER, 190, 115, hour = 14)

        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)

        assertEquals(120, a.overall!!.systolic)
        assertEquals(1, a.readingsExcludedAsNonQualifying)
    }

    @Test
    fun `no result until the minimum number of days is reached`() {
        // Days 1 and 2 only: after discarding day 1, just one day of data.
        val readings = (1..2).flatMap { day ->
            listOf(reading(day, Occasion.MORNING, 130, 82), reading(day, Occasion.EVENING, 130, 82))
        }
        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)

        assertEquals(1, a.daysWithData)
        assertFalse(a.hasEnoughForResult)
        assertNull("must not classify off a single day", a.category)
    }

    @Test
    fun `three days is enough for a provisional result`() {
        val readings = (1..4).flatMap { day ->
            listOf(reading(day, Occasion.MORNING, 140, 90), reading(day, Occasion.EVENING, 140, 90))
        }
        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)

        assertEquals(3, a.daysWithData)
        assertTrue(a.hasEnoughForResult)
        assertFalse(a.isComplete)
        assertEquals(BpCategory.HYPERTENSION, a.category)
    }

    @Test
    fun `the average is classified against home thresholds not office`() {
        // 136/84 averaged: hypertensive at home (>=135), merely elevated in a clinic.
        val readings = (1..7).flatMap { day ->
            listOf(reading(day, Occasion.MORNING, 136, 84), reading(day, Occasion.EVENING, 136, 84))
        }
        val home = Protocol722.assess(readings, start, Guideline.ESC_2024, MeasurementSetting.HOME, zone)
        val office = Protocol722.assess(readings, start, Guideline.ESC_2024, MeasurementSetting.OFFICE, zone)

        assertEquals(BpCategory.HYPERTENSION, home.category)
        assertEquals(BpCategory.ELEVATED, office.category)
    }

    @Test
    fun `morning and evening are averaged separately to expose the morning surge`() {
        val readings = (1..7).flatMap { day ->
            listOf(reading(day, Occasion.MORNING, 145, 90), reading(day, Occasion.EVENING, 125, 78))
        }
        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)

        assertEquals(145, a.morning!!.systolic)
        assertEquals(125, a.evening!!.systolic)
        assertEquals(20, a.morningEveningSystolicDelta)
        assertEquals(135, a.overall!!.systolic)
    }

    @Test
    fun `readings outside the window are ignored`() {
        val readings = fullWeek(systolic = 120, diastolic = 75).toMutableList()
        readings += reading(9, Occasion.MORNING, 200, 120)    // two days past the window
        readings += reading(-2, Occasion.MORNING, 200, 120)   // before the window

        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)
        assertEquals(120, a.overall!!.systolic)
        assertEquals(24, a.readingsUsed)
    }

    @Test
    fun `an empty window reports nothing rather than zero`() {
        val a = Protocol722.assess(emptyList(), start, Guideline.ESC_2024, zone = zone)
        assertNull(a.overall)
        assertNull(a.category)
        assertEquals(0, a.readingsUsed)
        assertEquals(0f, a.progressFraction, 0.001f)
    }

    @Test
    fun `pulse averages ignore readings that have no pulse`() {
        val readings = listOf(
            reading(2, Occasion.MORNING, 130, 80, pulse = 60),
            reading(3, Occasion.MORNING, 130, 80, pulse = 80),
            reading(4, Occasion.MORNING, 130, 80, pulse = null)
        )
        val a = Protocol722.assess(readings, start, Guideline.ESC_2024, zone = zone)
        assertEquals(70, a.overall!!.pulse)
    }
}
