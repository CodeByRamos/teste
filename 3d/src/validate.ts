// Validates the generated library (run after build):
//   1. every GLB honours the node contract the runtime depends on (parts with baseSize, mounts, roles);
//   2. geometry bounds match the layout's declared dimensions (scale sanity: millimetres → metres);
//   3. triangle and size budgets;
//   4. layouts stay valid across the parameter ranges found in OpenDB (no negative/NaN sizes).
// Exit code 1 on any failure. Writes docs/3d/validation.json.

import { readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { NodeIO, getBounds } from "@gltf-transform/core";
import { EXTMeshoptCompression, KHRMaterialsEmissiveStrength, KHRMeshQuantization } from "@gltf-transform/extensions";
import { MeshoptDecoder } from "meshoptimizer";
import {
  airCoolerLayout,
  boardLayout,
  caseLayout,
  gpuLayout,
  psuLayout,
  ramLayout,
  type Layout,
} from "../../frontend/src/lib/models3d/layout.ts";
import { BUDGET } from "./budget.ts";

const ROOT = resolve(import.meta.dirname, "..", "..", "frontend", "public");
const catalog = JSON.parse(readFileSync(join(ROOT, "3d-models", "catalog.json"), "utf8"));

await MeshoptDecoder.ready;
const io = new NodeIO()
  .registerExtensions([EXTMeshoptCompression, KHRMeshQuantization, KHRMaterialsEmissiveStrength])
  .registerDependencies({ "meshopt.decoder": MeshoptDecoder });

interface Result {
  modelId: string;
  ok: boolean;
  problems: string[];
  measuredMm?: number[];
  expectedMm?: number[];
}

const results: Result[] = [];
for (const model of catalog.models) {
  if (model.status !== "implemented" || !model.url) continue;
  const problems: string[] = [];
  const doc = await io.read(join(ROOT, model.url));
  const root = doc.getRoot();
  const nodes = root.listNodes();
  const byName = new Map(nodes.map((n) => [n.getName(), n]));

  const top = byName.get(model.modelId);
  if (!top) problems.push("root node missing");
  else if ((top.getExtras() as { modelId?: string }).modelId !== model.modelId) problems.push("root extras.modelId mismatch");

  for (const part of model.parts ?? []) {
    const node = byName.get(part);
    if (!node) {
      problems.push(`part node missing: ${part}`);
      continue;
    }
    const extras = node.getExtras() as { baseSize?: number[] };
    if (!Array.isArray(extras.baseSize) || extras.baseSize.some((v) => !(v >= 0))) problems.push(`invalid baseSize on ${part}`);
    const scale = node.getScale();
    if (scale.some((s) => Math.abs(s - 1) > 1e-6)) problems.push(`part ${part} has a baked scale (runtime scaling would compound)`);
  }
  for (const mount of model.mounts ?? []) {
    if (!byName.has(`mount_${mount}`)) problems.push(`mount missing: ${mount}`);
  }
  for (const material of root.listMaterials()) {
    if (!(material.getExtras() as { role?: string }).role) problems.push(`material without role: ${material.getName()}`);
  }

  // Bounds of visible geometry vs the layout's declared dimensions (tolerance: 4% or 3 mm).
  const bounds = getBounds(root.listScenes()[0]);
  const measured = [0, 1, 2].map((i) => Math.round((bounds.max[i] - bounds.min[i]) * 1000 * 10) / 10);
  const expected = [model.baseDimensionsMm.x, model.baseDimensionsMm.y, model.baseDimensionsMm.z];
  measured.forEach((value, i) => {
    const allowed = Math.max(3, expected[i] * 0.04);
    if (value > expected[i] + allowed) problems.push(`axis ${"xyz"[i]}: geometry ${value} mm exceeds declared ${expected[i]} mm`);
  });
  if (model.stats.triangles > BUDGET.triangles) problems.push(`triangles ${model.stats.triangles} > ${BUDGET.triangles}`);
  if (model.stats.bytes > BUDGET.bytes) problems.push(`size ${model.stats.bytes} B > ${BUDGET.bytes}`);

  results.push({ modelId: model.modelId, ok: problems.length === 0, problems, measuredMm: measured, expectedMm: expected });
}

// Parameter sweep over the ranges measured in OpenDB (docs/3d/opendb-analysis.json).
const sweep: string[] = [];
const valid = (name: string, layout: Layout) => {
  for (const [part, p] of Object.entries(layout.parts)) {
    if (p.size.some((v) => !Number.isFinite(v) || v < 0) || p.position.some((v) => !Number.isFinite(v))) {
      sweep.push(`${name}: invalid ${part} ${JSON.stringify(p)}`);
    }
  }
};
for (const length of [110, 170, 245, 308, 385]) {
  for (const slots of [1, 2, 2.5, 3, 3.5, 4]) {
    for (const fans of [1, 2, 3]) {
      valid(`gpu L${length} s${slots} f${fans}`, gpuLayout({ lengthMm: length, heightMm: 125, slots, fanCount: fans, fanSizeMm: 100, backplate: true, eightPinConnectors: 2, highPowerConnector: false }));
    }
  }
}
for (const form of ["atx", "matx", "itx", "eatx"] as const) valid(`board ${form}`, boardLayout({ form, ramSlots: 4, m2Slots: 3, x16Slots: 3 }));
for (const [w, h, d] of [[176, 222, 215], [235, 481, 465], [340, 700, 700]]) {
  valid(`case ${w}x${h}x${d}`, caseLayout({ form: "atx-mid", widthMm: w, heightMm: h, depthMm: d, psuShroud: true, glassSide: true, expansionSlots: 7 }));
}
for (const height of [30, 47, 130, 155, 172]) {
  for (const style of ["single-tower", "dual-tower", "low-profile"] as const) {
    valid(`cooler ${style} ${height}`, airCoolerLayout({ style, heightMm: height, fanSizeMm: 120, fanCount: 2 }));
  }
}
for (const length of [99, 150, 241]) valid(`psu ${length}`, psuLayout({ form: "atx", lengthMm: length, modular: true }));
for (const height of [30, 42, 57]) valid(`ram ${height}`, ramLayout({ heightMm: height, heatSpreader: true, rgb: true }));

const report = { generatedAt: new Date().toISOString(), models: results, parameterSweep: sweep.length ? sweep : "ok" };
writeFileSync(resolve(import.meta.dirname, "..", "..", "docs", "3d", "validation.json"), JSON.stringify(report, null, 2));
for (const r of results) {
  console.log(`${r.ok ? "ok  " : "FAIL"} ${r.modelId.padEnd(26)} ${r.measuredMm?.join(" × ")} mm${r.problems.length ? "  " + r.problems.join("; ") : ""}`);
}
console.log(`parameter sweep: ${sweep.length ? sweep.length + " problems\n" + sweep.join("\n") : "ok"}`);
if (results.some((r) => !r.ok) || sweep.length) process.exit(1);
