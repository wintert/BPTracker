# Can we build our own blood-pressure app for the Galaxy Watch?

**Short answer: no.** Not through any supported developer path, and not by a clever
workaround either. Four independent walls, each of which alone is enough to stop it.

## Wall 1 — Wear OS gives third-party apps no BP sensor at all

`MeasureClient` in AndroidX Health Services — the API for taking a spot health
measurement on a Wear OS watch — supports exactly **one** data type:

> Table 5: `MeasureClient` data types — **HEART_RATE_BPM**, beats per minute [Double]

That's the whole table. No blood pressure. `PassiveMonitoringClient` and
`ExerciseClient` are steps/distance/calories/HR/elevation — also no BP.

Easy trap to avoid: **Health Connect** *does* have a `BloodPressureRecord` type, and
Google Fit's `HealthDataTypes` lists blood pressure. Those are **storage** types — a
place to record a number your app already obtained. They are not a sensor API. Being
able to *write* a BP value is not being able to *measure* one.

## Wall 2 — Samsung's privileged SDK doesn't expose BP either

The Samsung Privileged Health SDK (a.k.a. Samsung Health Sensor SDK) is the deepest
access Samsung offers on Watch4-and-later, and it does hand out genuinely raw signals:

> Accelerometer · BIA · MF-BIA · **ECG (raw)** · EDA · Heart Rate (incl. inter-beat
> interval) · **PPG (raw)** · Skin Temperature · SpO2 · Sweat Loss

Blood pressure is **not** in that list. And the SDK isn't open anyway — it is "available
to selectively granted partners", with Samsung reviewing every application.

So: yes, the raw PPG waveform is technically reachable — but only as an approved
partner, and Samsung deliberately withheld BP as a derived type.

## Wall 3 — even raw PPG wouldn't get you a number

Samsung's BP feature is **not** a sensor reading. It is a pulse-wave-analysis *estimate*
calibrated against a real cuff:

- You must calibrate with a **traditional upper-arm cuff**, entering **three** separate
  cuff measurements.
- The calibration expires and must be repeated **every 28 days**. Miss the window
  (31 days) and the feature locks until you redo the full three-cuff process.
- The watch is estimating how *your* PPG waveform maps to *your* cuff baseline — and
  that mapping drifts, which is why the calibration expires.

Reimplementing that means building and validating a regulated PPG→BP model from
scratch. It's a research programme, not a weekend project — and the output would be an
uncalibrated guess.

## Wall 4 — the phone gate

Samsung Health Monitor, the app that hosts the BP feature, runs **only on Samsung
Galaxy phones** (Android 12+). It is a regulated, per-region-cleared medical feature —
the US rollout landed in April 2026. A Nothing Phone 3a Pro is outside the supported
configuration by design, not by oversight.

The community workaround is sideloading a patched Samsung Health Monitor APK onto a
non-Samsung phone. Mentioning it for completeness, not recommending it: it's an
unverified binary handling health data, it breaks on updates, and — most importantly —
it bypasses precisely the regional clearance and device validation that the accuracy
claim rests on. An unvalidated BP number is worse than no BP number.

## The clinical footnote that settles it

The 2025 ACC/AHA hypertension guideline is explicit that reliance on **cuffless devices,
including smartwatches**, for accurate BP should be **avoided** until they demonstrate
greater precision and reliability.

The user already owns the better instrument. A validated upper-arm cuff beats any wrist
estimate — including one on a Samsung phone.

## What the watch *is* still good for

Not nothing:

- **Health Connect write** — our app writes `BloodPressureRecord`
  (permission `android.permission.health.WRITE_BLOOD_PRESSURE`, with
  `measurementLocation` LEFT_ARM/RIGHT_ARM and `bodyPosition` SITTING_DOWN etc.),
  so readings flow to any other app the user chooses.
- **Resting heart rate / HRV** trends from the watch are real, supported data and sit
  usefully alongside BP.
- A small **Wear OS companion tile** for reminders ("evening reading, day 4 of 7") and
  fast manual entry from the wrist is entirely buildable — it just can't measure.

## Sources

- [Enhance app compatibility across Wear OS devices — Health Services data type tables (Android Developers)](https://developer.android.com/health-and-fitness/health-services/compatibility)
- [Take spot health measurements with MeasureClient (Android Developers)](https://developer.android.com/health-and-fitness/health-services/active-data/measure-client)
- [BloodPressureRecord — Health Connect (Android Developers)](https://developer.android.com/reference/androidx/health/connect/client/records/BloodPressureRecord)
- [Samsung Privileged Health SDK — overview (Samsung Developer)](https://developer.samsung.com/health/privileged/overview.html)
- [Samsung Health Sensor SDK — introduction & data types (Samsung Developer)](https://developer.samsung.com/health/sensor/guide/introduction.html)
- [Samsung's Blood Pressure Monitoring Feature Now Available to U.S. Users (Samsung Mobile Press)](https://www.samsungmobilepress.com/articles/samsung-health-blood-pressure-monitoring-us-galaxy-watch)
- [Samsung brings BP monitoring to Galaxy Watches in the US (Android Authority)](https://www.androidauthority.com/samsung-galaxy-watch-blood-pressure-monitoring-us-rollout-3653403/)
- [Samsung releases Galaxy Watch blood pressure feature in U.S. (MobiHealthNews)](https://www.mobihealthnews.com/news/samsung-releases-galaxy-watch-blood-pressure-feature-us)
- [2025 AHA/ACC High Blood Pressure Guideline (Circulation)](https://www.ahajournals.org/doi/10.1161/CIR.0000000000001356)
