// SINGLE SOURCE OF TRUTH for the vision extraction contract.
// The Android app's Kotlin data classes mirror this schema exactly.
// Change it here first, re-run the harness, then port.
//
// Design rules (see docs/CLINICAL-REFERENCE.md §6b):
//  - Every measured value is NULLABLE. strict:true would otherwise force the model
//    to invent a number from an unreadable photo. A null becomes an empty field the
//    user must type; it never becomes a default or a guess.
//  - The model reports what the DISPLAY shows, including averages, units, error
//    codes and IHB flags. Deciding what to do about them is the app's job, not the
//    model's.

export const SYSTEM_PROMPT = `
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
Layout varies by brand (Omron, Beurer, Microlife, Braun, Citizen). Use the printed
labels SYS/DIA/PULSE when visible; fall back to vertical position and magnitude only
when labels are absent. Systolic is ALWAYS the larger of the two pressure numbers.

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
  - display_datetime: many monitors show their own clock. Transcribe it exactly as
    shown. Do not convert or normalise; do not invent a year that is not displayed.

CONFIDENCE
Score each value 0.0-1.0 for how certain you are of every digit. Below ~0.9 means the
user should verify carefully. If you returned a value, be honest about how sure you are.
`.trim();

export const EXTRACTION_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["readable", "unreadable_reason", "device_display", "reading", "confidence", "notes"],
  properties: {
    readable: {
      type: "boolean",
      description: "True if this is a blood pressure monitor display with at least one legible value."
    },
    unreadable_reason: {
      type: ["string", "null"],
      description: "If readable is false, a short plain explanation (e.g. 'photo is of a person, not a monitor', 'display too blurred to read')."
    },
    device_display: {
      type: "object",
      additionalProperties: false,
      required: ["unit", "indicates_average_or_memory", "memory_slot_label", "user_profile", "error_code", "irregular_heartbeat", "display_datetime", "brand_text"],
      properties: {
        unit: { type: ["string", "null"], enum: ["mmHg", "kPa", null] },
        indicates_average_or_memory: {
          type: "boolean",
          description: "True ONLY for an averaged or memory-recalled reading. A person-icon user-profile selector does NOT count."
        },
        memory_slot_label: { type: ["string", "null"], description: "Literal memory marker shown, e.g. 'A', 'M 12'. Null if this is a fresh reading." },
        user_profile: { type: ["string", "null"], description: "User-profile slot shown beside a person icon, e.g. '1' or '2'. Not a memory indicator." },
        error_code: { type: ["string", "null"], description: "Literal error code shown, e.g. 'E1'. Null if none." },
        irregular_heartbeat: { type: "boolean" },
        display_datetime: { type: ["string", "null"], description: "Date/time as printed on the device, transcribed literally." },
        brand_text: { type: ["string", "null"], description: "Any brand/model text visible, e.g. 'OMRON M3'." }
      }
    },
    reading: {
      type: "object",
      additionalProperties: false,
      required: ["systolic", "diastolic", "pulse"],
      properties: {
        systolic: { type: ["integer", "null"], description: "Upper value. Null if not confidently legible." },
        diastolic: { type: ["integer", "null"], description: "Lower value. Null if not confidently legible." },
        pulse: { type: ["integer", "null"], description: "Pulse/bpm. Null if absent or not confidently legible." }
      }
    },
    confidence: {
      type: "object",
      additionalProperties: false,
      required: ["systolic", "diastolic", "pulse"],
      properties: {
        systolic: { type: "number", description: "0.0-1.0. Use 0 when the value is null." },
        diastolic: { type: "number" },
        pulse: { type: "number" }
      }
    },
    notes: { type: ["string", "null"], description: "Anything else on the display worth surfacing. Null if nothing." }
  }
};

// Client-side gate. Runs on EVERY extraction regardless of reported confidence.
// Mirrored in Kotlin as ReadingValidator. See CLINICAL-REFERENCE.md §6b.
export const LIMITS = {
  systolic:  { min: 60, max: 300 },
  diastolic: { min: 30, max: 200 },
  pulse:     { min: 30, max: 220 },
  pulsePressure: { min: 10, max: 100 }
};

export function validate(result) {
  const problems = [];
  const r = result?.reading ?? {};
  const d = result?.device_display ?? {};

  if (result?.readable === false) problems.push({ level: "block", msg: `Not readable: ${result.unreadable_reason ?? "unspecified"}` });
  if (d.error_code) problems.push({ level: "block", msg: `Monitor shows error code ${d.error_code} — this is not a valid measurement.` });
  if (d.indicates_average_or_memory) problems.push({ level: "block", msg: `Display appears to show a stored average/memory entry${d.memory_slot_label ? ` (${d.memory_slot_label})` : ""} — must not be logged as a fresh reading.` });
  if (d.unit === "kPa") problems.push({ level: "block", msg: "Monitor is set to kPa. Switch it to mmHg, or convert explicitly (1 kPa ≈ 7.5 mmHg)." });

  for (const [field, lim] of Object.entries(LIMITS)) {
    if (field === "pulsePressure") continue;
    const v = r[field];
    if (v == null) { problems.push({ level: "needs-input", msg: `${field} could not be read — type it in.` }); continue; }
    if (v < lim.min || v > lim.max) problems.push({ level: "block", msg: `${field} ${v} is outside the plausible range ${lim.min}-${lim.max}.` });
  }

  if (r.systolic != null && r.diastolic != null) {
    if (r.systolic <= r.diastolic) {
      problems.push({ level: "block", msg: `Systolic (${r.systolic}) must exceed diastolic (${r.diastolic}) — digits were probably transposed or misread.` });
    } else {
      const pp = r.systolic - r.diastolic;
      if (pp < LIMITS.pulsePressure.min || pp > LIMITS.pulsePressure.max) {
        problems.push({ level: "confirm", msg: `Pulse pressure is ${pp} mmHg, which is unusual — please confirm both numbers against the photo.` });
      }
    }
  }

  const c = result?.confidence ?? {};
  for (const f of ["systolic", "diastolic", "pulse"]) {
    if (r[f] == null) continue;
    const conf = c[f] ?? 0;
    if (conf < 0.75) {
      problems.push({ level: "retake", msg: `Couldn't read ${f} clearly (${Math.round(conf * 100)}%). Check it against the photo, or retake with less glare.` });
    } else if (conf < 0.9) {
      problems.push({ level: "confirm", msg: `Not fully sure of ${f} (${Math.round(conf * 100)}%) — please verify against the photo.` });
    }
  }

  return { ok: !problems.some(p => p.level === "block"), problems };
}

// How each problem level renders in the review screen. Extraction is a convenience,
// never a gate: the user can always type the numbers and save regardless.
//
//   block       field cleared, red banner, cannot save until corrected by hand
//   retake      value shown but flagged amber, "Retake photo" button offered,
//               field focused so typing over it is the path of least resistance
//   confirm     value prefilled, amber underline, must be tapped to acknowledge
//   needs-input empty field, neutral prompt to type it
//   (none)      value prefilled, green tick, save is one tap
export const REVIEW_LEVELS = ["block", "retake", "confirm", "needs-input"];
