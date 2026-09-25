// Generates the model library: catalog entry → parametric builder → GLB (two levels of detail) → catalog.json.
//
// Usage:
//   node src/build.ts                  build every implemented model
//   node src/build.ts gpu-dual-fan-v1  build only the given model ids
//   node src/build.ts --spec file.json bake one component-specific GLB ({ modelId, params, out })
//
// Output: frontend/public/3d-models/<category>/<variant>/<modelId>.glb (+ .lod1.glb) and catalog.json.

import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { CATALOG, type ModelDefinition } from "./catalog.ts";
import { exportGlb, type ModelDraft } from "./kit/export.ts";
import type { Detail } from "./kit/geometry.ts";
import { buildGpu } from "./families/gpu.ts";
import { buildMotherboard } from "./families/motherboard.ts";
import { buildCase } from "./families/case.ts";
import { buildAirCooler, buildDrive, buildFan, buildM2, buildPsu, buildPump, buildRadiator, buildRam } from "./families/small-parts.ts";

const OUT = resolve(import.meta.dirname, "..", "..", "frontend", "public", "3d-models");
const ANALYSIS = resolve(import.meta.dirname, "..", "..", "docs", "3d", "opendb-analysis.json");

function draft(def: ModelDefinition, params: Record<string, unknown>, detail: Detail): ModelDraft {
  const p = params as never;
  switch (def.builder) {
    case "gpu": return buildGpu(def.modelId, def.variant, p, detail);
    case "motherboard": return buildMotherboard(def.modelId, def.variant, p, detail);
    case "case": return buildCase(def.modelId, def.variant, p, detail);
    case "air-cooler": return buildAirCooler(def.modelId, def.variant, p, detail);
    case "ram": return buildRam(def.modelId, def.variant, p);
    case "psu": return buildPsu(def.modelId, def.variant, p, detail);
    case "fan": return buildFan(def.modelId, def.variant, p, detail);
    case "m2": return buildM2(def.modelId, def.variant, p);
    case "drive": return buildDrive(def.modelId, def.variant, (params as { bay: "2.5" | "3.5" }).bay);
    case "radiator": return buildRadiator(def.modelId, def.variant, p, detail);
    case "pump": return buildPump(def.modelId, detail);
    case "planned": throw new Error(`${def.modelId} is planned, not implemented`);
  }
}

function coverage(): Record<string, { records: number; weighted: number; current: number }> {
  if (!existsSync(ANALYSIS)) return {};
  const analysis = JSON.parse(readFileSync(ANALYSIS, "utf8"));
  const all: Record<string, { records: number; weighted: number; current: number }> = {};
  for (const section of Object.values(analysis) as Array<{ families?: Record<string, never> }>) {
    if (section && typeof section === "object" && section.families) Object.assign(all, section.families);
  }
  return all;
}

async function main() {
  const args = process.argv.slice(2);
  if (args[0] === "--spec") {
    const spec = JSON.parse(readFileSync(args[1], "utf8"));
    const def = CATALOG.find((d) => d.modelId === spec.modelId);
    if (!def || def.status !== "implemented") throw new Error(`Unknown model ${spec.modelId}`);
    const result = await exportGlb(draft(def, { ...def.base, ...spec.params }, "high"));
    mkdirSync(dirname(resolve(spec.out)), { recursive: true });
    writeFileSync(resolve(spec.out), result.bytes);
    console.log(`baked ${spec.modelId} → ${spec.out} (${result.triangles} triangles, ${(result.bytes.length / 1024).toFixed(1)} KB)`);
    return;
  }

  const selected = CATALOG.filter((d) => d.status === "implemented" && (args.length === 0 || args.includes(d.modelId)));
  const covered = coverage();
  const entries = [];
  for (const def of CATALOG) {
    const entry: Record<string, unknown> = {
      modelId: def.modelId,
      category: def.category,
      variant: def.variant,
      status: def.status,
      description: def.description,
      parameters: def.parameters,
      coverage: covered[def.coverageKey] ?? null,
    };
    if (def.status === "implemented") {
      const dir = join(OUT, def.category, def.variant);
      const file = join(dir, `${def.modelId}.glb`);
      const lodFile = join(dir, `${def.modelId}.lod1.glb`);
      if (selected.includes(def)) {
        mkdirSync(dir, { recursive: true });
        const high = await exportGlb(draft(def, def.base, "high"));
        const low = await exportGlb(draft(def, def.base, "low"));
        writeFileSync(file, high.bytes);
        writeFileSync(lodFile, low.bytes);
        const d = draft(def, def.base, "high");
        entry.stats = { triangles: high.triangles, bytes: high.bytes.length, lod1Triangles: low.triangles, lod1Bytes: low.bytes.length, materials: high.materials };
        entry.baseDimensionsMm = dims(d.layout.bounds);
        entry.parts = Object.keys(d.layout.parts);
        entry.mounts = Object.keys(d.layout.mounts);
        console.log(`${def.modelId.padEnd(26)} ${String(high.triangles).padStart(6)} tris ${(high.bytes.length / 1024).toFixed(1).padStart(6)} KB   lod1 ${String(low.triangles).padStart(6)} tris ${(low.bytes.length / 1024).toFixed(1).padStart(6)} KB`);
      } else {
        const previous = readPrevious().find((e) => e.modelId === def.modelId);
        Object.assign(entry, previous ?? {}, entry);
        delete entry.url;
        delete entry.lod1Url;
      }
      entry.base = def.base;
      // Only publish URLs for files that exist, so the runtime never requests a model that was not generated.
      if (existsSync(file)) entry.url = `/3d-models/${relative(OUT, file).replaceAll("\\", "/")}`;
      if (existsSync(lodFile)) entry.lod1Url = `/3d-models/${relative(OUT, lodFile).replaceAll("\\", "/")}`;
    }
    entries.push(entry);
  }
  mkdirSync(OUT, { recursive: true });
  writeFileSync(join(OUT, "catalog.json"), JSON.stringify({ version: 1, units: "m", models: entries }, null, 2));
  console.log(`\n${selected.length} models built → ${relative(process.cwd(), OUT)}`);
}

function dims(bounds: { min: number[]; max: number[] }) {
  return {
    x: Math.round((bounds.max[0] - bounds.min[0]) * 10) / 10,
    y: Math.round((bounds.max[1] - bounds.min[1]) * 10) / 10,
    z: Math.round((bounds.max[2] - bounds.min[2]) * 10) / 10,
  };
}

function readPrevious(): Array<Record<string, unknown>> {
  const file = join(OUT, "catalog.json");
  return existsSync(file) ? JSON.parse(readFileSync(file, "utf8")).models : [];
}

await main();
