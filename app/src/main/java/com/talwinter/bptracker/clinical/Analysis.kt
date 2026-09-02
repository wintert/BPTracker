package com.talwinter.bptracker.clinical

import com.talwinter.bptracker.data.Arm
import com.talwinter.bptracker.data.Reading
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Analyses that fall out of data the app already collects but never looked at.
 */
object Analysis {

    /**
     * Inter-arm difference.
     *
     * A consistent gap between arms is itself a clinical signal, not noise: a systolic
     * difference above ~10 mmHg is worth mentioning to a doctor, and above ~15 mmHg is
     * associated with elevated cardiovascular risk. It also has a practical consequence —
     * once a difference is established, all future readings should use the higher arm,
     * otherwise the log understates the real pressure.
     *
     * See docs/CLINICAL-REFERENCE.md section 5.
     */
    const val NOTABLE_ARM_DIFFERENCE = 10
    const val SIGNIFICANT_ARM_DIFFERENCE = 15

    /** Below this many readings per arm the comparison is noise, not a finding. */
    const val MIN_READINGS_PER_ARM = 3

    data class ArmComparison(
        val leftCount: Int,
        val rightCount: Int,
        val leftSystolic: Int,
        val rightSystolic: Int,
        val leftDiastolic: Int,
        val rightDiastolic: Int
    ) {
        val higherArm: Arm get() = if (leftSystolic >= rightSystolic) Arm.LEFT else Arm.RIGHT
        val systolicDifference: Int get() = abs(leftSystolic - rightSystolic)
        val diastolicDifference: Int get() = abs(leftDiastolic - rightDiastolic)

        val isNotable: Boolean get() = systolicDifference >= NOTABLE_ARM_DIFFERENCE
        val isSignificant: Boolean get() = systolicDifference >= SIGNIFICANT_ARM_DIFFERENCE
    }

    /**
     * Null when either arm has too few readings to say anything — which is the normal
     * state for someone who sensibly uses one arm consistently.
     */
    fun compareArms(readings: List<Reading>): ArmComparison? {
        val usable = readings.filterNot { it.excludeFromAverages }
        val left = usable.filter { it.arm == Arm.LEFT }
        val right = usable.filter { it.arm == Arm.RIGHT }
        if (left.size < MIN_READINGS_PER_ARM || right.size < MIN_READINGS_PER_ARM) return null

        return ArmComparison(
            leftCount = left.size,
            rightCount = right.size,
            leftSystolic = left.map { it.systolic }.average().roundToInt(),
            rightSystolic = right.map { it.systolic }.average().roundToInt(),
            leftDiastolic = left.map { it.diastolic }.average().roundToInt(),
            rightDiastolic = right.map { it.diastolic }.average().roundToInt()
        )
    }

    /**
     * Centred rolling mean of systolic and diastolic, for drawing a trend line through
     * noisy individual readings. Window shrinks at the ends rather than dropping points,
     * so the line spans the whole chart instead of stopping short.
     */
    fun rollingAverage(readings: List<Reading>, window: Int = 5): List<Pair<Float, Float>> {
        if (readings.isEmpty()) return emptyList()
        val ordered = readings.sortedBy { it.timestamp }
        val half = window / 2
        return ordered.indices.map { i ->
            val slice = ordered.subList(
                (i - half).coerceAtLeast(0),
                (i + half + 1).coerceAtMost(ordered.size)
            )
            slice.map { it.systolic }.average().toFloat() to
                slice.map { it.diastolic }.average().toFloat()
        }
    }

    /**
     * Variability of systolic pressure across a window. High visit-to-visit variability is
     * an independent risk marker, so it is worth surfacing alongside the average rather
     * than hiding inside it.
     */
    fun systolicStandardDeviation(readings: List<Reading>): Double? {
        val values = readings.filterNot { it.excludeFromAverages }.map { it.systolic.toDouble() }
        if (values.size < 2) return null
        val mean = values.average()
        return kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }
}
