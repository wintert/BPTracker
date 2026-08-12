package com.talwinter.bptracker.extract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors tools/extract-test/extraction-contract.mjs, which is the source of truth.
 * That harness is where prompt changes get proven against real photos before landing here.
 *
 * Verified against a Transtek monitor: 153/84/72 at 99/99/98% confidence, correctly
 * distinguishing the user-profile icon from a memory recall.
 */
object ExtractionContract {

    val SYSTEM_PROMPT = """
You extract blood pressure readings from photographs of home blood pressure monitor
displays. Your output becomes a medical record, so a wrong number is far worse than
a missing one.

CORE RULE: If you cannot read a value with high confidence, return null for it. Never
guess, never infer from context, never "round to a plausible reading". A null costs the
user two seconds of typing. A wrong number corrupts their health record silently.

WHAT YOU ARE LOOKING AT
Consumer upper-arm cuff monitors show, typically top to bottom:
  - SYS / Systolic  - the largest number, uppermost. Typically 90-180.
  - DIA / Diastolic - below it, smaller value. Typically 50-110.
  - PULSE / PUL / heart icon - lowest. Typically 50-100.
Layout varies by brand (Omron, Beurer, Microlife, Braun, Citizen, Transtek). Use the
printed labels SYS/DIA/PULSE when visible; fall back to vertical position and magnitude
only when labels are absent. Systolic is ALWAYS the larger of the two pressure numbers.

SEVEN-SEGMENT DIGITS ARE THE HARD PART
These displays lose segments and are easily misread:
  - 8 vs 0 vs 6 vs 9    - a faded segment turns 8 into 0, 6 or 9
  - 1 vs 7              - 1 is two right-hand segments only
  - 5 vs 6, 3 vs 9
If a digit is ambiguous because of glare, angle, a dying LCD, or motion blur, return
null for that whole value rather than committing to a reading.

USER PROFILE vs MEMORY RECALL - DO NOT CONFUSE THESE
Many monitors support two users and show a PERSON/TORSO ICON next to a small 1 or 2.
That is a USER PROFILE selector. It means "these are user 1's settings". It is a
perfectly normal FRESH reading. Put the slot in user_profile and leave
indicates_average_or_memory = false.
A MEMORY RECALL is different: the letter M, MR, A, AVG or AVE rendered as TEXT, usually
with a record index like "M 12" or an averaging symbol, shown after pressing the memory
button. Only that sets indicates_average_or_memory = true.
Wrongly flagging a user-profile icon as memory blocks a valid reading. Be careful.

THINGS THAT ARE NOT A FRESH READING - report them, do not silently treat them as data:
  - AVERAGE / MEMORY RECALL: as defined above. Set indicates_average_or_memory = true.
  - ERROR CODES: E1-E6, Er, EE, "Err", "HI", "LO". Put the literal code in error_code
    and return null for all three measured values.
  - A blank or powered-off display.

THE DEVICE CLOCK IS USUALLY WRONG
Most people never set the clock on their monitor, so displays routinely show a default
or stale date years in the past. Transcribe display_datetime exactly as shown, including
the field order printed on the panel (many are D/M/YY, not M/D/YY - read the tiny
"D M YY" header above the digits if present). Never correct it, never normalise it, and
never assume it is the real time of the reading. The app trusts the photo's own EXIF
timestamp over this field.

A COLOURED CLASSIFICATION BAR (red/amber/green segments beside SYS/DIA) is the monitor's
own WHO/ESH risk indicator. It is decoration - ignore it. The app classifies readings
itself against the guideline the user selected.

OTHER DISPLAY FEATURES
  - unit: almost always mmHg. Some monitors toggle to kPa (values then read roughly
    8-24 instead of 60-180). Report which you see; if no unit is printed but values
    are in the 60-250 range, it is mmHg.
  - irregular_heartbeat: a heart icon with a slash, or "IHB"/arrhythmia indicator.

CONFIDENCE
Score each value 0.0-1.0 for how certain you are of every digit. Below ~0.9 means the
user should verify carefully. If you returned a value, be honest about how sure you are.
""".trimIndent()

    /** Strict json_schema for the Responses API. Kept as literal JSON to match the harness byte for byte. */
    val JSON_SCHEMA = """
{
  "type": "object",
  "additionalProperties": false,
  "required": ["readable", "unreadable_reason", "device_display", "reading", "confidence", "notes"],
  "properties": {
    "readable": { "type": "boolean" },
    "unreadable_reason": { "type": ["string", "null"] },
    "device_display": {
      "type": "object",
      "additionalProperties": false,
      "required": ["unit", "indicates_average_or_memory", "memory_slot_label", "user_profile", "error_code", "irregular_heartbeat", "display_datetime", "brand_text"],
      "properties": {
        "unit": { "type": ["string", "null"], "enum": ["mmHg", "kPa", null] },
        "indicates_average_or_memory": { "type": "boolean" },
        "memory_slot_label": { "type": ["string", "null"] },
        "user_profile": { "type": ["string", "null"] },
        "error_code": { "type": ["string", "null"] },
        "irregular_heartbeat": { "type": "boolean" },
        "display_datetime": { "type": ["string", "null"] },
        "brand_text": { "type": ["string", "null"] }
      }
    },
    "reading": {
      "type": "object",
      "additionalProperties": false,
      "required": ["systolic", "diastolic", "pulse"],
      "properties": {
        "systolic": { "type": ["integer", "null"] },
        "diastolic": { "type": ["integer", "null"] },
        "pulse": { "type": ["integer", "null"] }
      }
    },
    "confidence": {
      "type": "object",
      "additionalProperties": false,
      "required": ["systolic", "diastolic", "pulse"],
      "properties": {
        "systolic": { "type": "number" },
        "diastolic": { "type": "number" },
        "pulse": { "type": "number" }
      }
    },
    "notes": { "type": ["string", "null"] }
  }
}
""".trimIndent()
}

@Serializable
data class ExtractionResult(
    val readable: Boolean,
    @SerialName("unreadable_reason") val unreadableReason: String? = null,
    @SerialName("device_display") val deviceDisplay: DeviceDisplay,
    val reading: ExtractedReading,
    val confidence: Confidence,
    val notes: String? = null
) {
    /** Lowest confidence among values that were actually returned. Null if nothing was read. */
    val lowestConfidence: Float?
        get() = listOfNotNull(
            reading.systolic?.let { confidence.systolic },
            reading.diastolic?.let { confidence.diastolic },
            reading.pulse?.let { confidence.pulse }
        ).minOrNull()
}

@Serializable
data class DeviceDisplay(
    val unit: String? = null,
    @SerialName("indicates_average_or_memory") val indicatesAverageOrMemory: Boolean = false,
    @SerialName("memory_slot_label") val memorySlotLabel: String? = null,
    @SerialName("user_profile") val userProfile: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("irregular_heartbeat") val irregularHeartbeat: Boolean = false,
    @SerialName("display_datetime") val displayDatetime: String? = null,
    @SerialName("brand_text") val brandText: String? = null
)

@Serializable
data class ExtractedReading(
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val pulse: Int? = null
)

@Serializable
data class Confidence(
    val systolic: Float = 0f,
    val diastolic: Float = 0f,
    val pulse: Float = 0f
)

/**
 * How a problem is surfaced in the review screen. Extraction is a convenience and never
 * a gate — the user can always type the numbers by hand and save regardless of what
 * the model said.
 */
enum class ReviewLevel { BLOCK, RETAKE, CONFIRM, NEEDS_INPUT }

data class ReviewProblem(val level: ReviewLevel, val message: String)

object ExtractionReview {
    /**
     * 0.75 is comfortably below normal operation: a glare-affected digit on a real photo
     * still scored 0.99, so this fires on genuinely bad shots rather than nagging.
     */
    private const val RETAKE_BELOW = 0.75f
    private const val CONFIRM_BELOW = 0.90f

    fun review(result: ExtractionResult): List<ReviewProblem> {
        val problems = mutableListOf<ReviewProblem>()
        val d = result.deviceDisplay
        val r = result.reading

        if (!result.readable) problems += ReviewProblem(
            ReviewLevel.BLOCK,
            result.unreadableReason ?: "Couldn't read this as a blood pressure display."
        )
        d.errorCode?.let {
            problems += ReviewProblem(ReviewLevel.BLOCK, "The monitor is showing error $it — that isn't a valid measurement.")
        }
        if (d.indicatesAverageOrMemory) problems += ReviewProblem(
            ReviewLevel.BLOCK,
            "This looks like a stored average or memory entry${d.memorySlotLabel?.let { " ($it)" } ?: ""}, not a fresh reading. Saving it would skew your 7-day average."
        )
        if (d.unit == "kPa") problems += ReviewProblem(
            ReviewLevel.BLOCK,
            "The monitor is set to kPa. Switch it to mmHg and measure again."
        )

        fun check(name: String, value: Int?, conf: Float) {
            if (value == null) {
                problems += ReviewProblem(ReviewLevel.NEEDS_INPUT, "Couldn't read $name — type it in.")
                return
            }
            when {
                conf < RETAKE_BELOW -> problems += ReviewProblem(
                    ReviewLevel.RETAKE,
                    "$name is unclear (${(conf * 100).toInt()}%). Check it against the photo, or retake with less glare."
                )
                conf < CONFIRM_BELOW -> problems += ReviewProblem(
                    ReviewLevel.CONFIRM,
                    "Not fully sure of $name (${(conf * 100).toInt()}%) — please verify."
                )
            }
        }
        check("systolic", r.systolic, result.confidence.systolic)
        check("diastolic", r.diastolic, result.confidence.diastolic)
        if (r.pulse != null) check("pulse", r.pulse, result.confidence.pulse)

        return problems
    }
}
