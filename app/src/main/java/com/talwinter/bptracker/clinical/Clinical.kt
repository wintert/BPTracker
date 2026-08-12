package com.talwinter.bptracker.clinical

/**
 * Blood pressure classification.
 *
 * Every number in this file is transcribed from docs/CLINICAL-REFERENCE.md, which cites
 * its primary sources. Do not edit a threshold here without editing the doc first.
 *
 * The branches below are written out longhand rather than driven from a table. That is
 * deliberate: this is medical logic that a human needs to be able to audit line by line
 * against a published guideline. Clever beats readable almost everywhere except here.
 */

/** Which published standard to classify against. Chosen by the user on first run. */
enum class Guideline(val displayName: String, val shortName: String) {
    ESC_2024("European Society of Cardiology 2024", "ESC 2024"),
    ACC_AHA_2025("American College of Cardiology / AHA 2025", "ACC/AHA 2025")
}

/**
 * Where the reading was taken. This is THE most important setting in the app.
 *
 * Home readings run lower than clinic readings because there is no white-coat effect.
 * Hypertension is >=140/90 in a clinic but >=135/85 at home. Classify home readings
 * against office thresholds and you under-call hypertension on essentially everyone.
 */
enum class MeasurementSetting { HOME, OFFICE }

/**
 * severity is the sortable rank shared across both guidelines, so UI colour and
 * "worst reading this week" logic work without caring which standard is active.
 */
enum class BpCategory(val displayName: String, val severity: Int) {
    NON_ELEVATED("Non-elevated", 0),   // ESC only
    NORMAL("Normal", 0),               // ACC/AHA only
    ELEVATED("Elevated", 1),           // both
    STAGE_1("Stage 1 hypertension", 2),// ACC/AHA only
    STAGE_2("Stage 2 hypertension", 3),// ACC/AHA only
    HYPERTENSION("Hypertension", 3),   // ESC only
    CRISIS("Hypertensive crisis", 4);  // both

    val isHypertensive: Boolean get() = severity >= 2
}

object Clinical {

    /** >=180 systolic OR >=120 diastolic. Identical under both guidelines. */
    const val CRISIS_SYSTOLIC = 180
    const val CRISIS_DIASTOLIC = 120

    fun isCrisis(systolic: Int, diastolic: Int): Boolean =
        systolic >= CRISIS_SYSTOLIC || diastolic >= CRISIS_DIASTOLIC

    /**
     * Classify a single reading.
     *
     * Note the OR logic throughout: 115/92 is hypertensive on diastolic alone. A reading
     * always takes the WORSE of its two components, never an average of them.
     */
    fun classify(
        systolic: Int,
        diastolic: Int,
        guideline: Guideline,
        setting: MeasurementSetting = MeasurementSetting.HOME
    ): BpCategory {
        require(systolic > diastolic) {
            "Systolic ($systolic) must exceed diastolic ($diastolic); refusing to classify."
        }
        if (isCrisis(systolic, diastolic)) return BpCategory.CRISIS

        return when (guideline) {
            Guideline.ACC_AHA_2025 -> classifyAccAha(systolic, diastolic, setting)
            Guideline.ESC_2024 -> classifyEsc(systolic, diastolic, setting)
        }
    }

    /**
     * ACC/AHA 2025 (categories unchanged from 2017).
     *
     * OFFICE            Normal <120 and <80 | Elevated 120-129 and <80
     *                   Stage 1 130-139 or 80-89 | Stage 2 >=140 or >=90
     * HOME equivalents  120/80 -> 120/80, 130/80 -> 130/80, but 140/90 -> 135/85,
     *                   so Stage 1 is 130-134 or 80-84 and Stage 2 starts at 135/85.
     */
    private fun classifyAccAha(sbp: Int, dbp: Int, setting: MeasurementSetting): BpCategory {
        val stage2Sbp = if (setting == MeasurementSetting.HOME) 135 else 140
        val stage2Dbp = if (setting == MeasurementSetting.HOME) 85 else 90

        return when {
            sbp >= stage2Sbp || dbp >= stage2Dbp -> BpCategory.STAGE_2
            sbp >= 130 || dbp >= 80 -> BpCategory.STAGE_1
            sbp >= 120 -> BpCategory.ELEVATED   // dbp is necessarily < 80 by now
            else -> BpCategory.NORMAL
        }
    }

    /**
     * ESC 2024. Three treatment-oriented bands; "non-elevated" deliberately replaced
     * "normal" because risk is continuous even below 120 systolic.
     *
     * OFFICE  Non-elevated <120/70 | Elevated 120-139 or 70-89 | Hypertension >=140/90
     *
     * HOME    ESC publishes a home equivalent only for the hypertension threshold
     *         (140/90 -> 135/85). It gives no home equivalent for the 120/70 boundary,
     *         so we leave that unshifted. This is a documented app-level decision, not a
     *         guideline statement -- see CLINICAL-REFERENCE.md section 2b, and it is
     *         surfaced in the app's "about these thresholds" text.
     */
    private fun classifyEsc(sbp: Int, dbp: Int, setting: MeasurementSetting): BpCategory {
        val htnSbp = if (setting == MeasurementSetting.HOME) 135 else 140
        val htnDbp = if (setting == MeasurementSetting.HOME) 85 else 90

        return when {
            sbp >= htnSbp || dbp >= htnDbp -> BpCategory.HYPERTENSION
            sbp >= 120 || dbp >= 70 -> BpCategory.ELEVATED
            else -> BpCategory.NON_ELEVATED
        }
    }

    /** Treatment target under both guidelines, for reference in the UI. */
    const val TREATMENT_TARGET_SYSTOLIC = 130
    const val TREATMENT_TARGET_DIASTOLIC = 80
}

/** Values computed from a reading rather than stored. */
object Derived {
    /**
     * Pulse pressure = SBP - DBP. Normal is around 40.
     * Persistently >60 suggests arterial stiffness and is an independent risk marker in
     * older adults; <25 is also abnormal.
     */
    fun pulsePressure(systolic: Int, diastolic: Int): Int = systolic - diastolic

    /** Mean arterial pressure, the standard approximation. Normal roughly 70-100. */
    fun meanArterialPressure(systolic: Int, diastolic: Int): Int =
        diastolic + (systolic - diastolic) / 3
}

/**
 * Plausibility gate. Mirrors tools/extract-test/extraction-contract.mjs so an AI-extracted
 * value and a hand-typed value face exactly the same checks.
 *
 * This runs on EVERY reading regardless of source or reported confidence.
 */
object ReadingValidator {
    val SYSTOLIC_RANGE = 60..300
    val DIASTOLIC_RANGE = 30..200
    val PULSE_RANGE = 30..220
    private val PLAUSIBLE_PULSE_PRESSURE = 10..100

    sealed interface Problem {
        val message: String

        /** Something is wrong with what was entered. Prevents saving and is shown in red. */
        data class Blocking(override val message: String) : Problem

        /**
         * A required field is simply empty. Prevents saving, but must NOT be rendered as
         * an error — greeting someone with red text on a blank form is hostile, and the
         * disabled Save button already says everything that needs saying.
         */
        data class Missing(override val message: String) : Problem

        /** Worth a second look, but the user may proceed. */
        data class Warning(override val message: String) : Problem
    }

    fun validate(systolic: Int?, diastolic: Int?, pulse: Int?): List<Problem> {
        val problems = mutableListOf<Problem>()

        if (systolic == null) problems += Problem.Missing("Enter a systolic value.")
        else if (systolic !in SYSTOLIC_RANGE)
            problems += Problem.Blocking("Systolic $systolic is outside the plausible range ${SYSTOLIC_RANGE.first}-${SYSTOLIC_RANGE.last}.")

        if (diastolic == null) problems += Problem.Missing("Enter a diastolic value.")
        else if (diastolic !in DIASTOLIC_RANGE)
            problems += Problem.Blocking("Diastolic $diastolic is outside the plausible range ${DIASTOLIC_RANGE.first}-${DIASTOLIC_RANGE.last}.")

        if (pulse != null && pulse !in PULSE_RANGE)
            problems += Problem.Blocking("Pulse $pulse is outside the plausible range ${PULSE_RANGE.first}-${PULSE_RANGE.last}.")

        if (systolic != null && diastolic != null) {
            if (systolic <= diastolic) {
                // The signature seven-segment failure: 121/81 misread as 211/18.
                problems += Problem.Blocking(
                    "Systolic ($systolic) must be higher than diastolic ($diastolic). The digits were probably misread — check the photo."
                )
            } else {
                val pp = Derived.pulsePressure(systolic, diastolic)
                if (pp !in PLAUSIBLE_PULSE_PRESSURE)
                    problems += Problem.Warning("Pulse pressure is $pp mmHg, which is unusual. Please double-check both numbers.")
            }
        }
        return problems
    }

    /** Both Blocking and Missing prevent saving; only Blocking is worth displaying. */
    fun canSave(problems: List<Problem>): Boolean =
        problems.none { it is Problem.Blocking || it is Problem.Missing }

    fun displayable(problems: List<Problem>): List<Problem> =
        problems.filterNot { it is Problem.Missing }
}
