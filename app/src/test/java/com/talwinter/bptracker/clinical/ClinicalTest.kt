package com.talwinter.bptracker.clinical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * These tests exist because a misclassification is silent. A wrong threshold produces a
 * plausible-looking category on every screen and nobody ever notices.
 *
 * Expected values are transcribed from docs/CLINICAL-REFERENCE.md.
 */
class ClinicalTest {

    private fun home(s: Int, d: Int, g: Guideline) = Clinical.classify(s, d, g, MeasurementSetting.HOME)
    private fun office(s: Int, d: Int, g: Guideline) = Clinical.classify(s, d, g, MeasurementSetting.OFFICE)

    // ---------- The bug this whole app is designed to avoid ----------

    @Test
    fun `home and office disagree exactly where the guidelines say they should`() {
        // 137/86: hypertensive at home (>=135/85), but only Stage 1 in a clinic (<140/90).
        assertEquals(BpCategory.STAGE_2, home(137, 86, Guideline.ACC_AHA_2025))
        assertEquals(BpCategory.STAGE_1, office(137, 86, Guideline.ACC_AHA_2025))

        assertEquals(BpCategory.HYPERTENSION, home(137, 86, Guideline.ESC_2024))
        assertEquals(BpCategory.ELEVATED, office(137, 86, Guideline.ESC_2024))
    }

    @Test
    fun `the users own reading classifies correctly`() {
        // 153/84 from the Transtek photo. Systolic alone is hypertensive at home.
        assertEquals(BpCategory.STAGE_2, home(153, 84, Guideline.ACC_AHA_2025))
        assertEquals(BpCategory.HYPERTENSION, home(153, 84, Guideline.ESC_2024))
    }

    // ---------- ACC/AHA 2025, home thresholds ----------

    @Test
    fun `acc aha home boundaries`() {
        val g = Guideline.ACC_AHA_2025
        assertEquals(BpCategory.NORMAL, home(119, 79, g))
        assertEquals(BpCategory.ELEVATED, home(120, 79, g))
        assertEquals(BpCategory.ELEVATED, home(129, 79, g))
        assertEquals(BpCategory.STAGE_1, home(130, 79, g))
        assertEquals(BpCategory.STAGE_1, home(134, 84, g))
        assertEquals(BpCategory.STAGE_2, home(135, 84, g))
        assertEquals(BpCategory.STAGE_2, home(134, 85, g))
    }

    @Test
    fun `diastolic alone drives the category - the OR rule`() {
        val g = Guideline.ACC_AHA_2025
        // Systolic is perfectly normal; diastolic is not. Must not be called Normal.
        assertEquals(BpCategory.STAGE_1, home(115, 82, g))
        assertEquals(BpCategory.STAGE_2, home(110, 88, g))
        // A reading is never the average of its two components: 128/92 averages to
        // something benign but the diastolic alone makes it Stage 2.
        assertEquals(BpCategory.STAGE_2, home(128, 92, g))
        assertEquals(BpCategory.STAGE_1, home(128, 82, g))
    }

    @Test
    fun `elevated requires diastolic under 80 not merely systolic in range`() {
        // 125/83 is Stage 1 on diastolic, NOT Elevated. Classic off-by-one in hand-rolled logic.
        assertEquals(BpCategory.STAGE_1, home(125, 83, Guideline.ACC_AHA_2025))
    }

    // ---------- ESC 2024, home thresholds ----------

    @Test
    fun `esc home boundaries`() {
        val g = Guideline.ESC_2024
        assertEquals(BpCategory.NON_ELEVATED, home(119, 69, g))
        assertEquals(BpCategory.ELEVATED, home(120, 69, g))
        assertEquals(BpCategory.ELEVATED, home(119, 70, g))  // diastolic alone
        assertEquals(BpCategory.ELEVATED, home(134, 84, g))
        assertEquals(BpCategory.HYPERTENSION, home(135, 84, g))
        assertEquals(BpCategory.HYPERTENSION, home(134, 85, g))
    }

    // ---------- Crisis ----------

    @Test
    fun `crisis overrides everything under both guidelines and both settings`() {
        for (g in Guideline.entries) for (s in MeasurementSetting.entries) {
            assertEquals(BpCategory.CRISIS, Clinical.classify(180, 90, g, s))
            assertEquals(BpCategory.CRISIS, Clinical.classify(150, 120, g, s))  // diastolic alone
            assertEquals(BpCategory.CRISIS, Clinical.classify(220, 130, g, s))
        }
        assertFalse(Clinical.isCrisis(179, 119))
        assertTrue(Clinical.isCrisis(180, 70))
        assertTrue(Clinical.isCrisis(130, 120))
    }

    // ---------- Coverage: the test that catches a threshold gap ----------

    @Test
    fun `every plausible reading gets exactly one category with no gaps`() {
        var checked = 0
        for (g in Guideline.entries) for (setting in MeasurementSetting.entries) {
            for (sbp in ReadingValidator.SYSTOLIC_RANGE) {
                for (dbp in ReadingValidator.DIASTOLIC_RANGE) {
                    if (sbp <= dbp) continue
                    // Throwing, or returning a category from the wrong guideline, fails here.
                    val c = Clinical.classify(sbp, dbp, g, setting)
                    val legal = when (g) {
                        Guideline.ACC_AHA_2025 -> c in setOf(
                            BpCategory.NORMAL, BpCategory.ELEVATED,
                            BpCategory.STAGE_1, BpCategory.STAGE_2, BpCategory.CRISIS
                        )
                        Guideline.ESC_2024 -> c in setOf(
                            BpCategory.NON_ELEVATED, BpCategory.ELEVATED,
                            BpCategory.HYPERTENSION, BpCategory.CRISIS
                        )
                    }
                    assertTrue("$g/$setting produced $c for $sbp/$dbp", legal)
                    checked++
                }
            }
        }
        assertTrue("expected a large sweep, only checked $checked", checked > 100_000)
    }

    @Test
    fun `severity increases monotonically as pressure rises`() {
        for (g in Guideline.entries) {
            var previous = -1
            for (sbp in 90..200) {
                val severity = Clinical.classify(sbp, 70, g, MeasurementSetting.HOME).severity
                assertTrue("severity went down at $sbp under $g", severity >= previous)
                previous = severity
            }
        }
    }

    // ---------- Derived values ----------

    @Test
    fun `derived values`() {
        assertEquals(69, Derived.pulsePressure(153, 84))
        assertEquals(107, Derived.meanArterialPressure(153, 84))  // 84 + 69/3
        assertEquals(93, Derived.meanArterialPressure(120, 80))   // 80 + 40/3 = 93.33 -> 93
    }

    // ---------- Validator ----------

    @Test
    fun `transposed seven segment digits are blocked`() {
        // 121/81 misread as 211/18 -- systolic and diastolic both in range individually.
        val problems = ReadingValidator.validate(211, 18, 72)
        assertFalse(ReadingValidator.canSave(problems))
    }

    @Test
    fun `systolic must exceed diastolic`() {
        assertFalse(ReadingValidator.canSave(ReadingValidator.validate(80, 80, 70)))
        assertFalse(ReadingValidator.canSave(ReadingValidator.validate(70, 90, 70)))
    }

    @Test
    fun `missing values block saving rather than defaulting`() {
        assertFalse(ReadingValidator.canSave(ReadingValidator.validate(null, 84, 72)))
        assertFalse(ReadingValidator.canSave(ReadingValidator.validate(153, null, 72)))
        // Pulse is genuinely optional -- some monitors do not show it.
        assertTrue(ReadingValidator.canSave(ReadingValidator.validate(153, 84, null)))
    }

    @Test
    fun `a good reading passes cleanly`() {
        val problems = ReadingValidator.validate(153, 84, 72)
        assertTrue(ReadingValidator.canSave(problems))
        assertTrue(problems.none { it is ReadingValidator.Problem.Blocking })
    }

    @Test
    fun `unusual pulse pressure warns but does not block`() {
        val problems = ReadingValidator.validate(200, 60, 70)   // pp = 140
        assertTrue(ReadingValidator.canSave(problems))
        assertTrue(problems.any { it is ReadingValidator.Problem.Warning })
    }

    @Test
    fun `out of range values are blocked`() {
        assertFalse(ReadingValidator.canSave(ReadingValidator.validate(400, 84, 72)))
        assertFalse(ReadingValidator.canSave(ReadingValidator.validate(153, 20, 72)))
        assertFalse(ReadingValidator.canSave(ReadingValidator.validate(153, 84, 300)))
    }
}
