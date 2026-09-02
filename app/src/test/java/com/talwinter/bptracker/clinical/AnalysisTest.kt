package com.talwinter.bptracker.clinical

import com.talwinter.bptracker.data.Arm
import com.talwinter.bptracker.data.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {

    private var clock = 1_750_000_000_000L

    private fun reading(
        systolic: Int,
        diastolic: Int = 80,
        arm: Arm = Arm.LEFT,
        exclude: Boolean = false
    ): Reading {
        clock += 3_600_000L
        return Reading(
            timestamp = clock,
            systolic = systolic,
            diastolic = diastolic,
            pulse = 70,
            arm = arm,
            excludeFromAverages = exclude
        )
    }

    // ---- Inter-arm ----

    @Test
    fun `no comparison until both arms have enough readings`() {
        // Someone sensibly using one arm consistently should get nothing, not a spurious finding.
        val oneArmOnly = (1..10).map { reading(130, arm = Arm.LEFT) }
        assertNull(Analysis.compareArms(oneArmOnly))

        val barelyRight = oneArmOnly + listOf(reading(150, arm = Arm.RIGHT), reading(150, arm = Arm.RIGHT))
        assertNull("two readings is not enough to call a difference", Analysis.compareArms(barelyRight))
    }

    @Test
    fun `a real difference is detected and attributed to the higher arm`() {
        val readings =
            (1..4).map { reading(130, 80, Arm.LEFT) } +
                (1..4).map { reading(148, 92, Arm.RIGHT) }

        val c = Analysis.compareArms(readings)!!
        assertEquals(130, c.leftSystolic)
        assertEquals(148, c.rightSystolic)
        assertEquals(18, c.systolicDifference)
        assertEquals(Arm.RIGHT, c.higherArm)
        assertTrue(c.isNotable)
        assertTrue(c.isSignificant)
    }

    @Test
    fun `a small difference is reported but not flagged`() {
        val readings =
            (1..4).map { reading(130, 80, Arm.LEFT) } +
                (1..4).map { reading(134, 82, Arm.RIGHT) }

        val c = Analysis.compareArms(readings)!!
        assertEquals(4, c.systolicDifference)
        assertFalse(c.isNotable)
    }

    @Test
    fun `the boundary values behave as documented`() {
        fun diff(rightSystolic: Int) = Analysis.compareArms(
            (1..3).map { reading(130, 80, Arm.LEFT) } +
                (1..3).map { reading(rightSystolic, 80, Arm.RIGHT) }
        )!!
        assertFalse(diff(139).isNotable)          // 9 mmHg
        assertTrue(diff(140).isNotable)           // 10 mmHg
        assertFalse(diff(144).isSignificant)      // 14 mmHg
        assertTrue(diff(145).isSignificant)       // 15 mmHg
    }

    @Test
    fun `excluded readings do not contribute to an arm comparison`() {
        val readings =
            (1..3).map { reading(130, 80, Arm.LEFT) } +
                (1..3).map { reading(130, 80, Arm.RIGHT) } +
                listOf(reading(220, 120, Arm.RIGHT, exclude = true))

        val c = Analysis.compareArms(readings)!!
        assertEquals(130, c.rightSystolic)
        assertEquals(3, c.rightCount)
    }

    // ---- Rolling average ----

    @Test
    fun `rolling average spans every point and smooths spikes`() {
        val readings = listOf(130, 130, 200, 130, 130).map { reading(it) }
        val rolled = Analysis.rollingAverage(readings, window = 5)

        assertEquals("must not drop points at the ends", readings.size, rolled.size)
        // The 200 spike is pulled well down by its neighbours.
        assertTrue("spike should be smoothed, was ${rolled[2].first}", rolled[2].first < 160f)
        assertTrue(rolled[2].first > 130f)
    }

    @Test
    fun `a flat series rolls to the same value`() {
        val rolled = Analysis.rollingAverage((1..6).map { reading(140, 90) })
        assertTrue(rolled.all { kotlin.math.abs(it.first - 140f) < 0.01f })
        assertTrue(rolled.all { kotlin.math.abs(it.second - 90f) < 0.01f })
    }

    @Test
    fun `rolling average of nothing is nothing`() {
        assertTrue(Analysis.rollingAverage(emptyList()).isEmpty())
    }

    // ---- Variability ----

    @Test
    fun `standard deviation needs at least two readings`() {
        assertNull(Analysis.systolicStandardDeviation(emptyList()))
        assertNull(Analysis.systolicStandardDeviation(listOf(reading(130))))
    }

    @Test
    fun `identical readings have no variability`() {
        assertEquals(0.0, Analysis.systolicStandardDeviation((1..4).map { reading(130) })!!, 0.001)
    }

    @Test
    fun `variability is the sample standard deviation`() {
        // 130, 140 -> mean 135, sample SD = sqrt(50/1) ... = 7.07
        val sd = Analysis.systolicStandardDeviation(listOf(reading(130), reading(140)))!!
        assertEquals(7.071, sd, 0.01)
    }
}
