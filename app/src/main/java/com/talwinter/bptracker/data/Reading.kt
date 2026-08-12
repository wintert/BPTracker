package com.talwinter.bptracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.talwinter.bptracker.clinical.Derived

/** Which arm. A consistent inter-arm difference >10-15 mmHg is worth mentioning to a doctor. */
enum class Arm { LEFT, RIGHT }

enum class BodyPosition { SITTING, STANDING, LYING }

/**
 * Drives 722 bucketing. OTHER readings are kept and charted but excluded from the
 * 7-day assessment, which is defined strictly in terms of morning and evening pairs.
 */
enum class Occasion { MORNING, EVENING, OTHER }

enum class MedicationState { BEFORE_MEDS, AFTER_MEDS, NOT_APPLICABLE }

/** Where the numbers came from. Matters when auditing a suspicious value months later. */
enum class ReadingSource { MANUAL, PHOTO_CAMERA, PHOTO_GALLERY }

@Entity(
    tableName = "readings",
    indices = [Index(value = ["timestamp"]), Index(value = ["excludeFromAverages"])]
)
data class Reading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Epoch millis. For gallery photos this is EXIF DateTimeOriginal, not import time. */
    val timestamp: Long,

    val systolic: Int,
    val diastolic: Int,
    /** Null is legitimate — not every monitor shows pulse, and it is never required. */
    val pulse: Int? = null,

    val arm: Arm = Arm.LEFT,
    val position: BodyPosition = BodyPosition.SITTING,
    val occasion: Occasion = Occasion.OTHER,
    val medicationState: MedicationState = MedicationState.NOT_APPLICABLE,

    /** The monitor's own irregular-heartbeat flag, if it showed one. */
    val irregularHeartbeat: Boolean = false,

    /**
     * Set for readings that must not pollute averages — most importantly a value read
     * off the monitor's memory/average display, which would otherwise double-count.
     */
    val excludeFromAverages: Boolean = false,

    val notes: String? = null,
    val deviceName: String? = null,

    // ---- Provenance ----
    val source: ReadingSource = ReadingSource.MANUAL,
    /** Local file URI of the photo this came from, kept so the value stays checkable. */
    val photoUri: String? = null,
    /** Lowest per-field confidence the model reported, 0..1. Null for manual entry. */
    val extractionConfidence: Float? = null,
    /** True if the user changed any AI-extracted value before saving. */
    val wasEditedAfterExtraction: Boolean = false
) {
    val pulsePressure: Int get() = Derived.pulsePressure(systolic, diastolic)
    val meanArterialPressure: Int get() = Derived.meanArterialPressure(systolic, diastolic)
}
