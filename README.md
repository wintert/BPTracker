# BP Tracker

A private, ad-free blood pressure log for one person. Records readings from a home cuff,
averages them the way the clinical protocol actually says to, and can read the numbers off
a photo of the monitor so they don't have to be typed.

Nothing is uploaded except a photo you explicitly choose to have read. No accounts, no
analytics, no ads.

## The two things that make it different

**It averages correctly.** Most trackers show you a chart of individual readings. Those
aren't clinically meaningful on their own. This implements the **722 protocol** — 7 days,
morning and evening, two readings per session, *day one discarded* — and treats that
average as the headline number. The home screen leads with it, not with your last reading.

**It uses home thresholds.** Hypertension is ≥140/90 in a clinic but **≥135/85 at home**,
because home readings have no white-coat effect. Applying clinic numbers to home readings
misclassifies almost everyone. Both ESC 2024 and ACC/AHA 2025 are supported, chosen on
first run rather than silently defaulted.

See [docs/CLINICAL-REFERENCE.md](docs/CLINICAL-REFERENCE.md) — the single source of truth
for every threshold, with primary sources cited. Code reads from it; don't change a number
in code without changing it there first.

## Building

```powershell
.\build.ps1 testDebugUnitTest    # clinical logic
.\build.ps1 installDebug         # build + install to the phone
.\build.ps1 assembleRelease      # signed APK (needs keystore.properties)
```

`build.ps1` exists because three things on this machine need working around; the reasons
are in its header. The important one: **TEMP must be a short local path**, or Gradle's
daemon dies with "Unable to establish loopback connection".

Toolchain: JDK 17, Android SDK at `C:\Projects\android-sdk`, Gradle 8.11.1 vendored under
`.tooling/`. There is no wrapper jar.

## Photo extraction

Optional. The app is completely usable without a key, a connection, or a camera.

Default model is `gpt-5.6-luna` — about **US$0.0012 per photo**, verified at 99% confidence
on a glare-affected seven-segment display.

Extraction is proven in [tools/extract-test](tools/extract-test/) *before* it changes in
the app. That harness holds the prompt, schema, and validator; the Kotlin in
`app/.../extract/` mirrors it. Iterate there — a run takes two seconds instead of a
rebuild-and-reinstall cycle — and add every verified photo to `truth.json` so a prompt
change can't silently break a monitor that already worked.

### Rules that are not negotiable

- **Every extracted value is nullable.** A `strict` schema with a required integer would
  force the model to invent a number from a blurry photo. Null becomes an empty field the
  user types.
- **Nothing is saved without review**, with the photo shown beside the fields.
- **Systolic ≤ diastolic is rejected** — the signature seven-segment failure is 121/81
  misread as 211/18.
- **A user-profile icon is not a memory recall.** Confusing them blocks valid readings;
  missing a real memory recall double-counts into the average.
- **Gallery photos use EXIF time, not now.** Otherwise an imported back-catalogue collapses
  onto today.

## What it deliberately does not do

- Diagnose, or use the word diagnosis.
- Comment on medication.
- Classify off a single reading without saying that a single reading means little.
- Read blood pressure from the Galaxy Watch. That isn't possible for a third-party app —
  the reasons, checked against the actual APIs, are in
  [docs/WATCH-FINDINGS.md](docs/WATCH-FINDINGS.md).

## Layout

```
app/src/main/java/com/talwinter/bptracker/
  clinical/    Classification, 722 protocol, validation. Pure logic, unit-tested.
  data/        Room entities, DAO, settings, photo storage.
  extract/     OpenAI vision client, mirroring the harness contract.
  ui/          Compose screens, design system.
  reminder/    Morning/evening alarms.
docs/          Clinical reference and watch findings.
tools/         Extraction harness.
```

## Status

Verified on a physical device (Nothing Phone 3a Pro, Android 16), debug and signed
release: onboarding and guideline choice, manual entry, live classification, 722 averaging
with the protocol grid, history, editing and deleting readings, CSV export, text scaling,
and encrypted key storage surviving R8.

**47 unit tests, 0 failures:**

| Suite | Covers |
|---|---|
| `ClinicalTest` | Both guidelines, home vs office, a sweep asserting every plausible reading lands in exactly one category |
| `Protocol722Test` | Day-1 discard, memory-recall exclusion, morning/evening split, partial weeks |
| `ExtractionReviewTest` | Memory-vs-user-profile, error codes, kPa, confidence tiers |
| `CsvExportTest` | RFC 4180 quoting, ordering, empty cells for absent pulse |

Not yet exercised on-device:

- **The photo → extraction → review path.** The extraction contract itself is proven
  against a real Transtek photo via the harness (153/84/72 at 99%), but the phone's
  camera → downscale → EXIF-rotate → upload chain hasn't been run end to end.
- **A reminder actually firing.** The settings screen, permission flow and scheduling are
  built and render correctly, but no notification has been observed arriving — that needs
  waiting for a real alarm, or setting one a minute ahead.
