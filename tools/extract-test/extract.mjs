#!/usr/bin/env node
// Harness to prove the OpenAI vision extraction against real photos of your cuff,
// BEFORE any of this is written in Kotlin.
//
//   set OPENAI_API_KEY=sk-...
//   node extract.mjs photo.jpg
//   node extract.mjs photo.jpg --model gpt-5.6-terra
//   node extract.mjs ./photos            (runs a whole folder, prints a summary table)
//
// Nothing here is uploaded anywhere except api.openai.com.

import { readFile, readdir, stat } from "node:fs/promises";
import { readFileSync, existsSync } from "node:fs";
import { extname, join, basename, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { SYSTEM_PROMPT, EXTRACTION_SCHEMA, validate } from "./extraction-contract.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));

// Key resolution, in order. The .env file is gitignored and is never printed,
// logged, or echoed anywhere by this script.
function loadKey() {
  if (process.env.OPENAI_API_KEY) return process.env.OPENAI_API_KEY.trim();
  const envPath = join(HERE, ".env");
  if (existsSync(envPath)) {
    for (const line of readFileSync(envPath, "utf8").split(/\r?\n/)) {
      const m = line.match(/^\s*OPENAI_API_KEY\s*=\s*(.+?)\s*$/);
      if (m) return m[1].replace(/^["']|["']$/g, "").trim();
    }
  }
  return null;
}

const API_KEY = loadKey();
if (!API_KEY) {
  console.error(`No API key found. Pick either:

  1. Create ${join(HERE, ".env")} containing one line:
       OPENAI_API_KEY=sk-...
     (already gitignored)

  2. Or set it as a persistent user env var, once:
       [Environment]::SetEnvironmentVariable('OPENAI_API_KEY','sk-...','User')
`);
  process.exit(1);
}

const args = process.argv.slice(2);
const modelFlag = args.indexOf("--model");
const MODEL = modelFlag !== -1 ? args[modelFlag + 1] : "gpt-5.6-luna";
const target = args.find(a => !a.startsWith("--") && a !== MODEL);
if (!target) { console.error("Usage: node extract.mjs <image-or-folder> [--model gpt-5.6-luna]"); process.exit(1); }

const MIME = { ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp", ".heic": "image/heic" };

const TRUTH = existsSync(join(HERE, "truth.json"))
  ? JSON.parse(readFileSync(join(HERE, "truth.json"), "utf8"))
  : {};

async function extract(imagePath) {
  const bytes = await readFile(imagePath);
  const mime = MIME[extname(imagePath).toLowerCase()] ?? "image/jpeg";

  const body = {
    model: MODEL,
    input: [{
      role: "user",
      content: [
        { type: "input_text", text: SYSTEM_PROMPT },
        { type: "input_image", image_url: `data:${mime};base64,${bytes.toString("base64")}`, detail: "high" }
      ]
    }],
    text: {
      format: {
        type: "json_schema",
        name: "blood_pressure_extraction",
        strict: true,
        schema: EXTRACTION_SCHEMA
      }
    }
  };

  const started = Date.now();
  const res = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${API_KEY}` },
    body: JSON.stringify(body)
  });
  const ms = Date.now() - started;

  const raw = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status}\n${raw}`);

  const payload = JSON.parse(raw);
  // Responses API: walk output for the first output_text chunk.
  let text = payload.output_text;
  if (!text) {
    for (const item of payload.output ?? []) {
      for (const c of item.content ?? []) {
        if (c.type === "output_text" && c.text) { text = c.text; break; }
      }
      if (text) break;
    }
  }
  if (!text) throw new Error("No output_text in response:\n" + JSON.stringify(payload, null, 2));

  return { result: JSON.parse(text), ms, usage: payload.usage };
}

function render(name, { result, ms, usage }) {
  const r = result.reading, d = result.device_display, c = result.confidence;
  const pct = v => `${Math.round((v ?? 0) * 100)}%`;
  const show = (v, conf) => v == null ? "—(null)" : `${v} (${pct(conf)})`;

  console.log(`\n${"=".repeat(64)}\n${name}   [${MODEL}, ${ms}ms]`);
  console.log(`  readable          ${result.readable}${result.unreadable_reason ? ` — ${result.unreadable_reason}` : ""}`);
  console.log(`  systolic          ${show(r.systolic, c.systolic)}`);
  console.log(`  diastolic         ${show(r.diastolic, c.diastolic)}`);
  console.log(`  pulse             ${show(r.pulse, c.pulse)}`);
  if (r.systolic != null && r.diastolic != null) {
    const pp = r.systolic - r.diastolic;
    console.log(`  pulse pressure    ${pp}   MAP ~${Math.round(r.diastolic + pp / 3)}`);
  }
  console.log(`  unit              ${d.unit ?? "—"}`);
  console.log(`  avg/memory        ${d.indicates_average_or_memory}${d.memory_slot_label ? ` (${d.memory_slot_label})` : ""}`);
  console.log(`  user profile      ${d.user_profile ?? "—"}`);
  console.log(`  error code        ${d.error_code ?? "—"}`);
  console.log(`  irregular beat    ${d.irregular_heartbeat}`);
  console.log(`  display clock     ${d.display_datetime ?? "—"}`);
  console.log(`  brand             ${d.brand_text ?? "—"}`);
  if (result.notes) console.log(`  notes             ${result.notes}`);
  if (usage) console.log(`  tokens            in ${usage.input_tokens} / out ${usage.output_tokens}`);

  const { ok, problems } = validate(result);
  console.log(`  VALIDATOR         ${ok ? "PASS — safe to offer for review" : "BLOCKED"}`);
  for (const p of problems) console.log(`    [${p.level}] ${p.msg}`);

  // Regression scoring against known-correct values, if truth.json has this image.
  const t = TRUTH[name];
  if (t) {
    const misses = ["systolic", "diastolic", "pulse"]
      .filter(f => t[f] !== undefined && r[f] !== t[f])
      .map(f => `${f} expected ${t[f]}, got ${r[f] ?? "null"}`);
    if (t.indicates_average_or_memory !== undefined && d.indicates_average_or_memory !== t.indicates_average_or_memory) {
      misses.push(`avg/memory expected ${t.indicates_average_or_memory}, got ${d.indicates_average_or_memory}`);
    }
    console.log(`  GROUND TRUTH      ${misses.length ? "MISMATCH" : "MATCH"}`);
    for (const m of misses) console.log(`    ✗ ${m}`);
  }
}

const info = await stat(target);
const files = info.isDirectory()
  ? (await readdir(target)).filter(f => MIME[extname(f).toLowerCase()]).map(f => join(target, f))
  : [target];

if (!files.length) { console.error("No images found."); process.exit(1); }
console.log(`Extracting ${files.length} image(s) with ${MODEL}...`);

for (const f of files) {
  try { render(basename(f), await extract(f)); }
  catch (e) { console.error(`\n${basename(f)}  FAILED: ${e.message}`); }
}
