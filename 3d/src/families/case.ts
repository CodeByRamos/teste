import { caseLayout, type CaseParams } from "../../../frontend/src/lib/models3d/layout.ts";
import { at, box, roundedBox, strips, studGrid, type Detail } from "../kit/geometry.ts";
import type { ModelDraft, Primitive } from "../kit/export.ts";

/**
 * PC case: the container every other model mounts into. Panels are separate parts so the runtime can resize the
 * case to a real product's dimensions (and hide the glass panel for a better view) without skewing details.
 */
export function buildCase(modelId: string, variant: string, params: CaseParams, detail: Detail): ModelDraft {
  const layout = caseLayout(params);
  const P = layout.parts;
  const parts: Record<string, Primitive[]> = {};
  const size = (name: string) => P[name].size;

  parts.tray_panel = [
    { geometry: box(...size("tray_panel")), material: "body" },
    // Cable cut-outs along the motherboard's front edge, as dark grommets.
    { geometry: at(strips([0.4, size("tray_panel")[1] * 0.5, 22], "y", 3, 0.55), size("tray_panel")[0] / 2 + 0.3, size("tray_panel")[1] * 0.05, size("tray_panel")[2] * 0.18), material: "dark" },
  ];
  parts.side_panel = [{ geometry: roundedBox(...size("side_panel"), 1.5), material: params.glassSide ? "glass" : "body" }];
  parts.top_panel = [
    { geometry: roundedBox(...size("top_panel"), 1), material: "body" },
    { geometry: at(studGrid(size("top_panel")[0] * 0.6, size("top_panel")[2] * 0.55, detail === "high" ? 14 : 7, detail === "high" ? 20 : 10, 3, 0.4, "y"), 10, 1.1, -size("top_panel")[2] * 0.05), material: "dark" },
  ];
  parts.bottom_panel = [{ geometry: box(...size("bottom_panel")), material: "body" }];

  const [fw, fh, fd] = size("front_panel");
  parts.front_panel = [
    { geometry: roundedBox(fw, fh, fd, 5, 3), material: "body" },
    // Mesh intake face: the most recognisable feature of a modern case front.
    { geometry: at(box(fw * 0.82, fh * 0.9, 1), 0, 0, fd / 2 + 0.4), material: "mesh" },
    { geometry: at(strips([fw * 0.8, fh * 0.88, 0.6], "y", detail === "high" ? 60 : 30, 0.5), 0, 0, fd / 2 + 1), material: "dark" },
  ];

  const [rw, rh, rd] = size("rear_panel");
  parts.rear_panel = [
    { geometry: box(rw, rh, rd), material: "body" },
    // Rear exhaust fan grille and I/O opening, as darker areas on the panel's outer face.
    { geometry: at(box(118, 118, 0.6), -rw / 2 + 1.5 + 8 + 75, rh / 2 - 28 - 70 - 5, -rd / 2 - 0.3), material: "mesh" },
    { geometry: at(box(46, 160, 0.6), -rw / 2 + 1.5 + 8 + 20, rh / 2 - 28 - 82, -rd / 2 - 0.3), material: "dark" },
  ];

  const [shw, shh, shd] = size("psu_shroud");
  parts.psu_shroud = [
    { geometry: roundedBox(shw, Math.max(shh, 1), shd, 2), material: "body" },
    { geometry: at(strips([shw * 0.35, 0.5, shd * 0.4], "z", 8, 0.5), shw * 0.15, Math.max(shh, 1) / 2 + 0.3, shd * 0.2), material: "dark" },
  ];

  const [ew, eh, ed] = size("expansion_slots");
  parts.expansion_slots = [{ geometry: strips([ew, eh, ed], "y", params.expansionSlots, 0.18), material: "metal" }];
  for (let i = 0; i < 4; i++) parts[`foot_${i}`] = [{ geometry: roundedBox(...size(`foot_${i}`), 3), material: "dark" }];

  return { modelId, family: "case", variant, baseParams: { ...params }, layout, parts };
}
