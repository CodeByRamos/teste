// Families with simple silhouettes: air cooler, RAM, PSU, case fan, storage, AIO.

import {
  airCoolerLayout,
  driveLayout,
  fanLayout,
  m2Layout,
  psuLayout,
  pumpLayout,
  radiatorLayout,
  ramLayout,
  type AirCoolerParams,
  type DriveBay,
  type FanParams,
  type M2Params,
  type PsuParams,
  type RadiatorParams,
  type RamParams,
} from "../../../frontend/src/lib/models3d/layout.ts";
import { at, box, cylinder, fanFrame, fanRotor, finStack, heatPipes, ring, roundedBox, strips, studGrid, type Detail } from "../kit/geometry.ts";
import type { ModelDraft, Primitive } from "../kit/export.ts";

function fanPrimitives(size: number, thickness: number, axis: "x" | "y" | "z", detail: Detail): Primitive[] {
  const frame = fanFrame(size, thickness, detail);
  const rotor = fanRotor(size - 10, thickness - 7, "z", detail, 7);
  if (axis === "x") {
    frame.rotateY(Math.PI / 2);
    rotor.rotateY(Math.PI / 2);
  }
  if (axis === "y") {
    frame.rotateX(Math.PI / 2);
    rotor.rotateX(Math.PI / 2);
  }
  return [
    { geometry: frame, material: "body" },
    { geometry: rotor, material: "blade" },
  ];
}

export function buildAirCooler(modelId: string, variant: string, params: AirCoolerParams, detail: Detail): ModelDraft {
  const layout = airCoolerLayout(params);
  const P = layout.parts;
  const parts: Record<string, Primitive[]> = {
    base: [
      { geometry: roundedBox(...P.base.size, 2), material: "copper" },
      { geometry: at(box(P.base.size[0] * 0.6, P.base.size[1] + 22, 10), 0, 0, 0), material: "metal" },
    ],
  };
  const pitch = detail === "high" ? 2.2 : 4.4;
  for (const name of Object.keys(P)) {
    const [x, y, z] = P[name].size;
    if (name === "heatpipes") parts.heatpipes = [{ geometry: heatPipes(x, y, z, 4, detail), material: "copper" }];
    if (name.startsWith("fin_stack_")) {
      // Tower fins are horizontal plates stacked upwards; low-profile fins are vertical plates under a top fan.
      const axis = params.style === "low-profile" ? "y" : "x";
      parts[name] = [{ geometry: finStack([x, y, z], axis, pitch, 0.5), material: "aluminium" }];
    }
    if (name.startsWith("top_cover_")) parts[name] = [{ geometry: roundedBox(x, y, z, 1), material: "body" }];
    if (name.startsWith("fan_")) {
      parts[name] = params.style === "low-profile"
        ? fanPrimitives(y, x, "x", detail)
        : fanPrimitives(x, z, "z", detail);
    }
  }
  return { modelId, family: "cooler", variant, baseParams: { ...params }, layout, parts };
}

export function buildRam(modelId: string, variant: string, params: RamParams): ModelDraft {
  const layout = ramLayout(params);
  const P = layout.parts;
  const [hx, hy, hz] = P.heatspreader.size;
  return {
    modelId,
    family: "ram",
    variant,
    baseParams: { ...params },
    layout,
    parts: {
      pcb: [
        { geometry: box(...P.pcb.size), material: "pcbGreen" },
        { geometry: at(box(1.2, 128, 1.3), -P.pcb.size[0] / 2 + 0.6, 0, 0), material: "copper" },
      ],
      chips: [{ geometry: strips(P.chips.size, "y", 8, 0.25), material: "dark" }],
      heatspreader: [
        { geometry: roundedBox(hx, hy, hz, 1.2), material: "body" },
        // Stamped relief lines that most spreaders have.
        { geometry: at(strips([hx * 0.5, hy * 0.7, 0.4], "y", 5, 0.8), -hx * 0.1, 0, hz / 2 + 0.1), material: "accent" },
      ],
      rgb_bar: [{ geometry: roundedBox(...P.rgb_bar.size, 1), material: "rgb" }],
    },
  };
}

export function buildPsu(modelId: string, variant: string, params: PsuParams, detail: Detail): ModelDraft {
  const layout = psuLayout(params);
  const P = layout.parts;
  const [w, h, l] = P.body.size;
  const [gw, gh] = P.rear_grille.size;
  const fan = P.fan_grille.size[0];
  return {
    modelId,
    family: "psu",
    variant,
    baseParams: { ...params },
    layout,
    parts: {
      body: [{ geometry: roundedBox(w, h, l, 2.5), material: "body" }],
      rear_grille: [
        { geometry: box(gw, gh, 1), material: "dark" },
        { geometry: at(studGrid(gw * 0.55, gh * 0.8, detail === "high" ? 14 : 7, detail === "high" ? 9 : 5, 4, 0.8, "z"), -gw * 0.18, 0, -0.6), material: "metal" },
      ],
      power_inlet: [{ geometry: box(...P.power_inlet.size), material: "connector" }],
      fan_grille: [
        { geometry: ring(fan / 2, fan / 2 - 3, 1.4, "y"), material: "metal" },
        { geometry: ring(fan * 0.32, fan * 0.32 - 2, 1.4, "y"), material: "metal" },
        { geometry: ring(fan * 0.16, fan * 0.16 - 2, 1.4, "y"), material: "metal" },
        { geometry: fanRotor(fan - 8, 1, "y", detail, 7).translate(0, 1, 0), material: "blade" },
      ],
      modular_panel: [
        { geometry: box(...P.modular_panel.size), material: "dark" },
        { geometry: at(studGrid(P.modular_panel.size[0] * 0.85, P.modular_panel.size[1] * 0.6, 6, 2, 9, 1.5, "z"), 0, 0, 0.8), material: "connector" },
      ],
      label: [{ geometry: box(...P.label.size), material: "sticker" }],
    },
  };
}

export function buildFan(modelId: string, variant: string, params: FanParams, detail: Detail): ModelDraft {
  const layout = fanLayout(params);
  const P = layout.parts;
  const S = P.frame.size[0];
  return {
    modelId,
    family: "fan",
    variant,
    baseParams: { ...params },
    layout,
    parts: {
      frame: [{ geometry: fanFrame(S, 25, detail), material: "body" }],
      rotor: [{ geometry: fanRotor(S - 10, 17, "z", detail, 7), material: "blade" }],
      hub: [
        { geometry: cylinder(S * 0.17, 20, "z", detail === "high" ? 32 : 16), material: "dark" },
        { geometry: at(cylinder(S * 0.13, 0.4, "z", 24), 0, 0, 10.2), material: "sticker" },
      ],
      rgb_ring: [{ geometry: ring(S / 2 - 3, S / 2 - 7, 2, "z"), material: "rgb" }],
    },
  };
}

export function buildM2(modelId: string, variant: string, params: M2Params): ModelDraft {
  const layout = m2Layout(params);
  const P = layout.parts;
  return {
    modelId,
    family: "storage",
    variant,
    baseParams: { ...params },
    layout,
    parts: {
      pcb: [{ geometry: box(...P.pcb.size), material: "pcb" }],
      nand: [{ geometry: strips(P.nand.size, "z", 2, 0.15), material: "dark" }],
      controller: [{ geometry: box(...P.controller.size), material: "dark" }],
      label: [{ geometry: box(...P.label.size), material: "sticker" }],
    },
  };
}

export function buildDrive(modelId: string, variant: string, bay: DriveBay): ModelDraft {
  const layout = driveLayout(bay);
  const P = layout.parts;
  return {
    modelId,
    family: "storage",
    variant,
    baseParams: { bay },
    layout,
    parts: {
      body: [{ geometry: roundedBox(...P.body.size, bay === "2.5" ? 1.5 : 2.5), material: "aluminium" }],
      label: [{ geometry: box(...P.label.size), material: "sticker" }],
      connector: [{ geometry: box(...P.connector.size), material: "connector" }],
    },
  };
}

export function buildRadiator(modelId: string, variant: string, params: RadiatorParams, detail: Detail): ModelDraft {
  const layout = radiatorLayout(params);
  const P = layout.parts;
  const parts: Record<string, Primitive[]> = {
    core: [
      { geometry: box(...P.core.size), material: "dark" },
      { geometry: finStack([P.core.size[0] - 4, P.core.size[1] - 4, P.core.size[2]], "z", detail === "high" ? 3 : 6, 1.4).translate(0, 0, 0), material: "accent" },
    ],
    tank_0: [{ geometry: roundedBox(...P.tank_0.size, 3), material: "body" }],
    tank_1: [{ geometry: roundedBox(...P.tank_1.size, 3), material: "body" }],
    fittings: [{ geometry: box(...P.fittings.size), material: "metal" }],
  };
  for (const name of Object.keys(P)) {
    if (name.startsWith("fan_")) parts[name] = fanPrimitives(P[name].size[0], P[name].size[1], "y", detail);
  }
  return { modelId, family: "aio", variant, baseParams: { ...params }, layout, parts };
}

export function buildPump(modelId: string, detail: Detail): ModelDraft {
  const layout = pumpLayout();
  const P = layout.parts;
  return {
    modelId,
    family: "aio",
    variant: "pump",
    baseParams: {},
    layout,
    parts: {
      block: [{ geometry: cylinder(P.block.size[1] / 2, P.block.size[0], "x", detail === "high" ? 48 : 24), material: "body" }],
      cap: [
        { geometry: cylinder(P.cap.size[1] / 2, P.cap.size[0], "x", detail === "high" ? 48 : 24), material: "accent" },
        { geometry: at(ring(P.cap.size[1] / 2, P.cap.size[1] / 2 - 3, P.cap.size[0] + 0.4, "x"), 0, 0, 0), material: "rgb" },
      ],
      fittings: [
        { geometry: at(cylinder(5, 14, "x", 16), 0, -9, 0), material: "metal" },
        { geometry: at(cylinder(5, 14, "x", 16), 0, 9, 0), material: "metal" },
      ],
    },
  };
}
