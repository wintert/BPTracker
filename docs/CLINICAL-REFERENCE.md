# Blood Pressure — Clinical Reference for the App

This is the single source of truth for every number the app uses to classify, average,
or warn. Code must read thresholds from here (encoded in one constants module), never
from scattered literals.

**The app is not a medical device.** It records and summarises readings taken by a
validated cuff. It does not diagnose, and it does not replace a clinician.

---

## 1. The single most important rule: home ≠ office

Home readings are systematically **lower** than clinic readings (no white-coat effect).
Applying office thresholds to home readings misclassifies almost everyone.

Corresponding values (the classic equivalence table):

| Setting | Hypertension threshold |
|---|---|
| Office / clinic | 140/90 |
| **Home (HBPM)** | **135/85** |
| Daytime ABPM | 135/85 |
| Night-time ABPM | 120/70 |
| 24-hour ABPM | 130/80 |

Every reading this user logs is **home/HBPM**. Default the app to home thresholds and
label the active guideline visibly on any screen that shows a category.

---

## 2. Two guideline families — pick one, never blend

The app supports both as a user setting. It must never mix numbers from the two into a
single table, because their category *names* overlap while their *boundaries* differ.

### 2a. ACC/AHA (2025 guideline — categories unchanged from 2017)

Office values:

| Category | Systolic | | Diastolic |
|---|---|---|---|
| Normal | < 120 | and | < 80 |
| Elevated | 120–129 | and | < 80 |
| Stage 1 hypertension | 130–139 | or | 80–89 |
| Stage 2 hypertension | ≥ 140 | or | ≥ 90 |

Home-equivalent cutoffs under ACC/AHA: 120/80 → 120/80, 130/80 → 130/80,
but 140/90 → **135/85**.

**HOME table — this is what the app actually uses. Encode these exact numbers:**

| Category | Systolic | | Diastolic |
|---|---|---|---|
| Normal | < 120 | and | < 80 |
| Elevated | 120–129 | and | < 80 |
| Stage 1 hypertension | 130–134 | or | 80–84 |
| Stage 2 hypertension | ≥ 135 | or | ≥ 85 |

Treatment target: **< 130/80**.

Note the OR logic: a reading of 128/92 is Stage 1 on diastolic alone. Classification
takes the **worse** of the two components. Verify at build time that the systolic bands
(<120 / 120–129 / 130–134 / ≥135) and diastolic bands (<80 / 80–84 / ≥85) are gap-free
and non-overlapping — a unit test should assert full integer coverage from 0 to 300.

### 2b. ESC 2024 (European — likely the local standard in Israel)

The ESC restructured its scheme in 2024 into three treatment-oriented bands:

| Category | Office BP |
|---|---|
| Non-elevated BP | < 120/70 |
| Elevated BP | 120–139 systolic **or** 70–89 diastolic |
| Hypertension | ≥ 140/90 (home ≥ **135/85**) |

"Non-elevated" replaced "normal" deliberately — the bands guide *treatment*, not
prognosis, because risk is continuous even below 120 systolic.

**HOME table — encode these exact numbers:**

| Category | Systolic | | Diastolic |
|---|---|---|---|
| Non-elevated BP | < 120 | and | < 70 |
| Elevated BP | 120–134 | or | 70–84 |
| Hypertension | ≥ 135 | or | ≥ 85 |

⚠️ **Documented app-level decision, not a guideline statement:** ESC 2024 publishes a
home equivalent only for the *hypertension* threshold (140/90 → 135/85). It does not
publish a home equivalent for the 120/70 non-elevated boundary. We keep 120/70 unshifted
for the lower bound. This is a deliberate, conservative choice — flag it in the app's
"about these thresholds" text so it is never mistaken for a guideline number.

**Guideline selection: ask on first run.** Do not silently default. A one-screen picker
("Which standard should I use? European ESC 2024 / American ACC-AHA 2025") removes an
entire class of "every reading was classified against the wrong standard" bug. ESC 2024
is the reasonable pre-selected option for a user in Israel/Europe, but the user confirms
it. Show the active guideline name next to every category badge, always.

---

## 3. Hypertensive crisis — the one hard safety rule

**≥ 180 systolic or ≥ 120 diastolic** is a hypertensive crisis and a medical emergency.

App behaviour on such a reading:
1. Do **not** silently save-and-move-on. Show a full-screen, unmissable alert.
2. Per AHA: wait 5 minutes and re-measure. Offer a "re-measure" action with a timer.
3. If still ≥ 180/120 **with** chest pain, shortness of breath, weakness/numbness,
   vision change, difficulty speaking, or back pain → call emergency services
   (101 in Israel / 911 US). Offer a one-tap dial.
4. If still ≥ 180/120 with no symptoms → contact a doctor promptly (urgency vs emergency).

Distinction worth encoding in the copy: **urgency** = severe BP, no organ damage;
**emergency** = severe BP + evidence of target-organ damage. The app cannot tell these
apart — it triages on symptoms and always errs toward "seek care".

Also worth a gentler flag: a reading **< 90/60** with symptoms (dizziness, fainting)
merits a note, though hypotension thresholds are far less standardised.

---

## 4. The 722 protocol — the summary that actually means something

This is the feature the ad-riddled apps do badly and the reason to build our own.

**7-2-2**: **7** consecutive days × **2** occasions per day (morning + evening) ×
**2** readings per occasion, taken 1–2 minutes apart.

Rules:
- Morning readings: within ~1 hour of waking, after urinating, **before** medication
  and before breakfast/coffee.
- Evening readings: before the evening meal or before bed, before evening medication.
- **Discard all of day 1.** First-day readings run high and are not representative.
- Average **every remaining reading** (days 2–7, all four per day = 24 readings).
- Minimum acceptable is 3 days if 7 aren't available; 7 is strongly preferred.
- Compare that average — not any individual reading — against the home thresholds above.

The app should render this as a first-class object: a "7-day assessment" card showing
progress (e.g. "day 4 of 7, 14 of 24 readings"), the running average, and the resulting
category once complete. Individual readings get a category badge too, but the copy must
make clear that **single readings do not diagnose anything**.

---

## 5. Correct measurement technique (show as an in-app checklist)

Bad technique is a far bigger error source than anything in the software.

- Validated **upper-arm** cuff, correct cuff size. Wrist devices are less reliable.
- No caffeine, exercise, or smoking for 30 minutes beforehand. Empty bladder.
- Sit **5 minutes quietly** first. No talking during the measurement.
- Back supported, **feet flat on the floor**, legs uncrossed.
- Arm bare (no sleeve rolled into a tourniquet), supported, cuff at **heart level**.
- Two readings 1–2 minutes apart; record both.
- Use the **same arm** every time. Establish which arm reads higher once (a consistent
  inter-arm difference > 10–15 mmHg is itself worth mentioning to a doctor) and then
  always use the higher-reading arm.

**Cuffless / smartwatch BP is explicitly not recommended** for real measurement by the
2025 ACC/AHA guideline — reliance on smartwatches for accurate BP should be avoided
until precision improves. See `docs/WATCH-FINDINGS.md`.

---

## 6. Fields to store per reading

Required: `systolic`, `diastolic`, `pulse`, `timestamp`.

Context fields that change interpretation — store them, because without them a trend
line is uninterpretable:

- `arm` — left / right
- `position` — sitting / standing / lying
- `occasion` — morning / evening / other (drives 722 bucketing)
- `medsTaken` — before / after medication
- `cuffSize`, `deviceName` — if more than one monitor is ever used
- `notes` — free text (stress, illness, poor sleep, salt, caffeine)
- `sourceImageUri` + `extractionConfidence` + `wasEdited` — provenance for OCR'd rows

Derived, computed not stored (or stored denormalised for query speed):

- **Pulse pressure** = SBP − DBP. Normal ≈ 40. Persistently > 60 suggests arterial
  stiffness and is an independent risk marker in older adults; < 25 is also abnormal.
- **MAP** (mean arterial pressure) ≈ DBP + ⅓(SBP − DBP). Normal ≈ 70–100.
- **Morning–evening difference** — a large morning surge is prognostically meaningful.
- **BP variability** (SD or coefficient of variation across the window).

---

## 6b. Reading the cuff display from a photo — domain traps

These are not OCR accuracy problems. They are "which reading is this, actually" problems,
and each one silently corrupts the 722 average while every individual number looks fine.

**Memory / average recall.** Nearly every consumer cuff can redisplay a *stored average*
or a *memory entry*, usually marked `A`, `AVG`, `M`, or a memory-slot number. If that is
logged as a fresh discrete reading, it double-counts into the 7-day average and skews it.
The extraction schema must carry `displayIndicatesAverageOrMemory: boolean`, and the
review screen must block saving (or force an explicit "yes, this is a stored average"
tag that excludes it from 722 math).

**Units.** Some monitors toggle mmHg / kPa. 1 kPa ≈ 7.5 mmHg, so a kPa reading logged as
mmHg is off by ~7.5×. Capture `unit` explicitly; convert or reject, never assume.

**Irregular heartbeat indicator.** Most cuffs flag IHB/arrhythmia with a heart-with-slash
icon. Clinically meaningful and worth capturing as `irregularHeartbeat: boolean` — it is
also a reason a single reading may be unreliable.

**Cuff-error codes.** `E1`–`E5`, `Er`, `EE` mean the measurement failed. Must never be
parsed as data.

**Timestamp source.** For a photo picked from the **gallery**, default the reading time
to EXIF `DateTimeOriginal`, *not* `now` — otherwise an imported back-catalogue of photos
all collapses onto today's date and the trend line is fiction. For a photo taken with the
in-app camera, `now` is correct. If the cuff's own display shows a date/time and it
conflicts with EXIF, surface both and let the user choose.

**Transposition is the characteristic seven-segment failure.** Segment misreads turn
121/81 into 211/18. Hard client-side gate, applied regardless of model confidence:

| Field | Accept range |
|---|---|
| Systolic | 60–300 |
| Diastolic | 30–200 |
| Pulse | 30–220 |

Plus the structural rule: **reject any reading where systolic ≤ diastolic.** That single
check catches most transpositions. Also flag pulse pressure (SBP − DBP) < 10 or > 100 as
implausible-needs-confirmation.

**Never let strict-schema mode invent a number.** A `strict: true` JSON schema with
`systolic` as a required integer *forces* the model to emit a value even from a blurry,
angled, or entirely wrong photo — which is exactly how a fabricated BP value enters a
medical log. Therefore: every extracted value is **nullable**, the response carries a
top-level `readable: boolean` and per-field confidence, and a null renders as an **empty
field the user must type**. Never substitute a default or a best guess.

The review screen shows **the photo beside the fields**, so the user verifies visually
rather than trusting a number they cannot check.

## 7. Things the app must NOT do

- Must not diagnose, or use the word "diagnosis".
- Must not recommend, adjust, or comment on medication or dosage.
- Must not classify off a single reading without a "one reading is not a diagnosis" caveat.
- Must not silently store an AI-extracted number. Every OCR'd value passes through an
  editable review screen before it is written.
- Must not present smartwatch-derived BP as equivalent to cuff BP.

---

## Sources

- [2024 ESC Guidelines for the management of elevated blood pressure and hypertension (European Heart Journal)](https://academic.oup.com/eurheartj/article/45/38/3912/7741010)
- [What Is New and Different in the 2024 ESC Guidelines (Hypertension, AHA Journals)](https://www.ahajournals.org/doi/10.1161/HYPERTENSIONAHA.124.24173)
- [2025 AHA/ACC High Blood Pressure Guideline (Circulation)](https://www.ahajournals.org/doi/10.1161/CIR.0000000000001356)
- [2025 High Blood Pressure Guideline — Top Things to Know (AHA Professional)](https://professional.heart.org/en/science-news/2025-high-blood-pressure-guideline/top-things-to-know)
- [2017 ACC/AHA Guideline for High Blood Pressure in Adults — Ten Points (ACC)](https://www.acc.org/Latest-in-Cardiology/ten-points-to-remember/2017/11/09/11/41/2017-Guideline-for-High-Blood-Pressure-in-Adults)
- [Diagnostic Thresholds for Blood Pressure Measured at Home (Hypertension)](https://www.ahajournals.org/doi/10.1161/HYPERTENSIONAHA.118.11657)
- [Standardized home blood pressure monitoring: rationale behind the 722 protocol (PMC)](https://pmc.ncbi.nlm.nih.gov/articles/PMC9532917/)
- [Number of Measurements Needed for a Reliable Estimate of Home BP (JAHA)](https://www.ahajournals.org/doi/10.1161/JAHA.118.008658)
- [When To Call 911 About High Blood Pressure (AHA)](https://www.heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/when-to-call-911-for-high-blood-pressure)
- [Hypertensive Crisis — StatPearls (NCBI)](https://www.ncbi.nlm.nih.gov/books/NBK507701/)
