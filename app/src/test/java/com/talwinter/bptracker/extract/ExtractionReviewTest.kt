package com.talwinter.bptracker.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * These encode the safety rules that stop an AI-read number becoming a wrong medical
 * record. They are cheap to break accidentally while tuning a prompt, and expensive to
 * notice: the failure is a plausible-looking value silently entering the log.
 */
class ExtractionReviewTest {

    private fun result(
        systolic: Int? = 128,
        diastolic: Int? = 82,
        pulse: Int? = 70,
        confidence: Float = 0.99f,
        readable: Boolean = true,
        unreadableReason: String? = null,
        unit: String? = "mmHg",
        averageOrMemory: Boolean = false,
        memoryLabel: String? = null,
        userProfile: String? = null,
        errorCode: String? = null
    ) = ExtractionResult(
        readable = readable,
        unreadableReason = unreadableReason,
        deviceDisplay = DeviceDisplay(
            unit = unit,
            indicatesAverageOrMemory = averageOrMemory,
            memorySlotLabel = memoryLabel,
            userProfile = userProfile,
            errorCode = errorCode,
            irregularHeartbeat = false,
            displayDatetime = null,
            brandText = "TRANSTEK"
        ),
        reading = ExtractedReading(systolic, diastolic, pulse),
        confidence = Confidence(confidence, confidence, confidence),
        notes = null
    )

    private fun levels(r: ExtractionResult) = ExtractionReview.review(r).map { it.level }

    @Test
    fun `a clean high-confidence reading raises nothing`() {
        assertTrue(ExtractionReview.review(result()).isEmpty())
    }

    // ---- The Transtek regression ----

    @Test
    fun `a user profile icon is not a memory recall`() {
        // The monitor shows a person icon beside "1". An earlier prompt read that as
        // memory slot M1 and blocked a perfectly valid reading.
        val problems = ExtractionReview.review(result(userProfile = "1"))
        assertTrue("user profile must not block: $problems", problems.isEmpty())
    }

    @Test
    fun `an actual memory recall is blocked`() {
        // Logging a stored average as a fresh reading double-counts it into the 7-day mean.
        val levels = levels(result(averageOrMemory = true, memoryLabel = "M 12"))
        assertTrue(levels.contains(ReviewLevel.BLOCK))
    }

    // ---- Display states that are not measurements ----

    @Test
    fun `an error code is blocked`() {
        assertTrue(levels(result(errorCode = "E1")).contains(ReviewLevel.BLOCK))
    }

    @Test
    fun `kPa is blocked rather than silently mis-scaled`() {
        // 1 kPa is about 7.5 mmHg — treating one as the other is a 7.5x error.
        assertTrue(levels(result(unit = "kPa", systolic = 17, diastolic = 11)).contains(ReviewLevel.BLOCK))
    }

    @Test
    fun `an unreadable photo is blocked and says why`() {
        val problems = ExtractionReview.review(
            result(readable = false, unreadableReason = "photo is of a cat, not a monitor")
        )
        assertTrue(problems.any { it.level == ReviewLevel.BLOCK })
        assertTrue(problems.first().message.contains("cat"))
    }

    // ---- Confidence tiers ----

    @Test
    fun `a null value asks for typing rather than inventing one`() {
        val problems = ExtractionReview.review(result(systolic = null))
        assertEquals(listOf(ReviewLevel.NEEDS_INPUT), problems.map { it.level })
        assertTrue(problems.first().message.contains("systolic"))
    }

    @Test
    fun `very low confidence offers a retake`() {
        assertTrue(levels(result(confidence = 0.5f)).contains(ReviewLevel.RETAKE))
    }

    @Test
    fun `middling confidence asks for a check but not a retake`() {
        val levels = levels(result(confidence = 0.82f))
        assertTrue(levels.contains(ReviewLevel.CONFIRM))
        assertFalse(levels.contains(ReviewLevel.RETAKE))
    }

    @Test
    fun `the glare case from the real photo stays silent`() {
        // 0.99 on a glare-affected digit is normal operation. If the thresholds ever creep
        // up, every single reading starts nagging and the warnings stop meaning anything.
        assertTrue(ExtractionReview.review(result(confidence = 0.99f)).isEmpty())
        assertTrue(ExtractionReview.review(result(confidence = 0.90f)).isEmpty())
    }

    @Test
    fun `a missing pulse is not treated as a problem`() {
        // Some monitors don't show pulse at all; it is genuinely optional.
        assertTrue(ExtractionReview.review(result(pulse = null)).isEmpty())
    }

    @Test
    fun `lowest confidence is reported across the values actually returned`() {
        val r = ExtractionResult(
            readable = true,
            unreadableReason = null,
            deviceDisplay = DeviceDisplay(unit = "mmHg"),
            reading = ExtractedReading(140, 90, null),
            confidence = Confidence(systolic = 0.97f, diastolic = 0.91f, pulse = 0.10f),
            notes = null
        )
        // Pulse was not returned, so its confidence must not drag the figure down.
        assertEquals(0.91f, r.lowestConfidence!!, 0.001f)
    }
}
