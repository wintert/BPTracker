# Extraction harness

Proves the photo → JSON pipeline against real photos of your monitor **before** any of it
is written in Kotlin. Iterate the prompt here, where a run takes two seconds instead of a
rebuild-and-reinstall cycle.

## Setup (once)

Create `.env` in this folder:

```
OPENAI_API_KEY=sk-...
```

It's gitignored, and the script never prints or logs it.

## Run

```bash
node C:\Projects\bp-tracker\tools\extract-test\extract.mjs C:\Projects\bp-tracker\tools\extract-test\BLOOD.jpeg
```

Whole folder, or a different model:

```bash
node extract.mjs ./photos --model gpt-5.6-terra
```

## What you get

Every field with a confidence score, then two checks:

- **VALIDATOR** — the same client-side gate the app will run. Blocks impossible values,
  systolic ≤ diastolic (the classic seven-segment transposition), memory/average recalls,
  error codes, and kPa. See `docs/CLINICAL-REFERENCE.md` §6b.
- **GROUND TRUTH** — compares against `truth.json` if that image is listed there.

## truth.json is the regression net

Every time you photograph a reading you've verified by eye, add it to `truth.json`. Then
any prompt change gets scored against **all** your monitors at once — so a tweak that
fixes a tricky photo can't silently break one that already worked.

## Files

| File | Purpose |
|---|---|
| `extraction-contract.mjs` | The prompt, JSON schema, and validator. **Single source of truth** — the Kotlin data classes mirror this. Change it here, prove it, then port. |
| `extract.mjs` | CLI runner and output formatter. |
| `truth.json` | Known-correct values per test image. |
