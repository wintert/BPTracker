package com.talwinter.bptracker.clinical

import com.talwinter.bptracker.data.Occasion
import com.talwinter.bptracker.data.Reading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The 722 home monitoring protocol: 7 consecutive days, 2 occasions per day
 * (morning + evening), 2 readings per occasion, taken 1-2 minutes apart.
 *
 * Day 1 is discarded entirely -- first-day readings run high and are not representative.
 * The average of days 2-7 is the number that carries clinical meaning. A single reading
 * does not, and the UI must never imply otherwise.
 *
 * See docs/CLINICAL-REFERENCE.md section 4.
 */
object Protocol722 {

    const val WINDOW_DAYS = 7
    const val READINGS_PER_OCCASION = 2
    const val OCCASIONS_PER_DAY = 2

    /** Days 2-7 inclusive, 2 occasions, 2 readings each. */
    const val EXPECTED_READINGS = (WINDOW_DAYS - 1) * OCCASIONS_PER_DAY * READINGS_PER_OCCASION

    /** Guidelines allow a shortened window when 7 days are not available. */
    const val MINIMUM_DAYS_FOR_RESULT = 3

    data class Average(val systolic: Int, val diastolic: Int, val pulse: Int?)

    data class Assessment(
        val windowStart: LocalDate,
        val windowEnd: LocalDate,
        /** Days from 2 onward that have at least one qualifying reading. */
        val daysWithData: Int,
        val readingsUsed: Int,
        val readingsDiscardedFromFirstDay: Int,
        val readingsExcludedAsNonQualifying: Int,
        val overall: Average?,
        val morning: Average?,
        val evening: Average?,
        val category: BpCategory?,
        val guideline: Guideline
    ) {
        val isComplete: Boolean get() = daysWithData >= WINDOW_DAYS - 1
        val hasEnoughForResult: Boolean get() = daysWithData >= MINIMUM_DAYS_FOR_RESULT
        val progressFraction: Float get() = (readingsUsed.toFloat() / EXPECTED_READINGS).coerceAtMost(1f)

        /**
         * The morning surge. A markedly higher morning average is prognostically
         * meaningful, so it is worth surfacing rather than hiding inside one number.
         */
        val morningEveningSystolicDelta: Int?
            get() = if (morning != null && evening != null) morning.systolic - evening.systolic else null
    }

    /**
     * Assess the 7-day window beginning at [windowStart].
     *
     * Excluded from the average, each counted separately so the UI can explain itself:
     *  - everything on day 1
     *  - readings marked excludeFromAverages (memory/average recalls off the monitor)
     *  - readings whose occasion is OTHER (the protocol is defined on morning/evening)
     */
    fun assess(
        readings: List<Reading>,
        windowStart: LocalDate,
        guideline: Guideline,
        setting: MeasurementSetting = MeasurementSetting.HOME,
        zone: ZoneId = ZoneId.systemDefault()
    ): Assessment {
        val windowEnd = windowStart.plusDays((WINDOW_DAYS - 1).toLong())

        val inWindow = readings.filter {
            val date = dateOf(it, zone)
            !date.isBefore(windowStart) && !date.isAfter(windowEnd)
        }

        val firstDayCount = inWindow.count { dateOf(it, zone) == windowStart }
        val afterFirstDay = inWindow.filter { dateOf(it, zone) != windowStart }

        val nonQualifying = afterFirstDay.count {
            it.excludeFromAverages || it.occasion == Occasion.OTHER
        }
        val qualifying = afterFirstDay.filter {
            !it.excludeFromAverages && it.occasion != Occasion.OTHER
        }

        val daysWithData = qualifying.map { dateOf(it, zone) }.distinct().size
        val overall = average(qualifying)
        val category = overall
            ?.takeIf { daysWithData >= MINIMUM_DAYS_FOR_RESULT }
            ?.let { Clinical.classify(it.systolic, it.diastolic, guideline, setting) }

        return Assessment(
            windowStart = windowStart,
            windowEnd = windowEnd,
            daysWithData = daysWithData,
            readingsUsed = qualifying.size,
            readingsDiscardedFromFirstDay = firstDayCount,
            readingsExcludedAsNonQualifying = nonQualifying,
            overall = overall,
            morning = average(qualifying.filter { it.occasion == Occasion.MORNING }),
            evening = average(qualifying.filter { it.occasion == Occasion.EVENING }),
            category = category,
            guideline = guideline
        )
    }

    /**
     * Assess the window ending today. Convenience for the home screen; a user who has
     * been logging continuously always has a "current week".
     */
    fun assessCurrentWindow(
        readings: List<Reading>,
        guideline: Guideline,
        setting: MeasurementSetting = MeasurementSetting.HOME,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Assessment = assess(
        readings, today.minusDays((WINDOW_DAYS - 1).toLong()), guideline, setting, zone
    )

    private fun dateOf(reading: Reading, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(reading.timestamp).atZone(zone).toLocalDate()

    private fun average(readings: List<Reading>): Average? {
        if (readings.isEmpty()) return null
        val pulses = readings.mapNotNull { it.pulse }
        return Average(
            systolic = readings.map { it.systolic }.average().roundToInt(),
            diastolic = readings.map { it.diastolic }.average().roundToInt(),
            pulse = if (pulses.isEmpty()) null else pulses.average().roundToInt()
        )
    }
}
