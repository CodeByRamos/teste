import { gpuLayout, SLOT_PITCH_MM, type GpuParams } from "../../../frontend/src/lib/models3d/layout.ts";
import { at, box, fanRotor, ring, roundedBox, strips, type Detail } from "../kit/geometry.ts";
import type { ModelDraft, Primitive } from "../kit/export.ts";

/**
 * Graphics card. One builder covers single/dual/triple-fan (and later blower/passive) designs: only the
 * number of fans changes the family, everything else is a parameter.
 */
export function buildGpu(modelId: string, variant: string, params: GpuParams, detail: Detail): ModelDraft {
  const layout = gpuLayout(params);
  const P = layout.parts;
  const parts: Record<string, Primitive[]> = {};

  const [bw, bh, bd] = P.bracket.size;
  const bracketSlots = Math.round(bh / SLOT_PITCH_MM + 0.1);
  parts.bracket = [
    { geometry: box(bw, bh, bd), material: "metal" },
    // Display outputs: a row of dark port openings on the outside of the bracket.
    { geometry: at(strips([bw * 0.62, 7, 1.4], "x", 4, 0.25), 8, bh / 2 - 12, -1), material: "dark" },
    // Vent slots in the remaining bracket height.
    ...(bracketSlots > 1
      ? [{ geometry: at(strips([bw * 0.8, bh - 24, 1.2], "y", Math.max(2, bracketSlots * 3), 0.55), 0, -8, -0.8), material: "dark" as const }]
      : []),
  ];

  const [ph, pt, pl] = P.pcb.size;
  parts.pcb = [{ geometry: box(ph, pt, pl), material: "pcb" }];
  parts.pcie_fingers = [{ geometry: box(...P.pcie_fingers.size), material: "copper" }];

  const [bph, bpt, bpl] = P.backplate.size;
  parts.backplate = [
    { geometry: roundedBox(bph, bpt, bpl, 1), material: "accent" },
    { geometry: at(strips([bph * 0.5, 0.4, bpl * 0.5], "z", 6, 0.6), 0, bpt / 2, -bpl * 0.1), material: "dark" },
  ];

  // Shroud: a rounded shell with a slim accent stripe along the top edge (the side people see).
  const [sh, st, sl] = P.shroud.size;
  parts.shroud = [
    { geometry: roundedBox(sh, st, sl, Math.min(6, st / 3), 3), material: "body" },
    { geometry: at(box(2, st * 0.35, sl * 0.8), sh / 2 + 0.6, st * 0.1, 0), material: "accent" },
  ];

  const fanNames = Object.keys(P).filter((name) => name.startsWith("fan_"));
  for (const name of fanNames) {
    const [d, t] = P[name].size;
    parts[name] = [
      { geometry: fanRotor(d - 6, t - 2, "y", detail, 9), material: "blade" },
      { geometry: ring(d / 2, d / 2 - 3, t, "y"), material: "dark" },
    ];
  }

  const [cx, cy, cz] = P.power_connector.size;
  parts.power_connector = [
    { geometry: box(cx, cy, cz), material: "connector" },
    { geometry: at(strips([1, cy * 0.6, cz * 0.8], "z", Math.max(3, Math.round(cz / 3)), 0.5), cx / 2 + 0.3, 0, 0), material: "dark" },
  ];

  return { modelId, family: "gpu", variant, baseParams: { ...params }, layout, parts };
}
