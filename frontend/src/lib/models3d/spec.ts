// What the 3D layer needs to draw one component: which base model, and the parameters that adapt it.
// Produced by the backend (ModelResolver) from structured component data; never guessed in the UI.

import {
  airCoolerLayout,
  boardLayout,
  caseLayout,
  driveLayout,
  fanLayout,
  gpuLayout,
  m2Layout,
  psuLayout,
  pumpLayout,
  radiatorLayout,
  ramLayout,
  type AirCoolerParams,
  type BoardParams,
  type CaseParams,
  type FanParams,
  type GpuParams,
  type Layout,
  type M2Params,
  type PsuParams,
  type RadiatorParams,
  type RamParams,
} from "./layout";

export type ModelFamily =
  | "gpu"
  | "motherboard"
  | "case"
  | "air-cooler"
  | "aio-radiator"
  | "aio-pump"
  | "ram"
  | "psu"
  | "fan"
  | "m2"
  | "drive-25"
  | "drive-35";

export interface ModelSpec {
  modelId: string;
  family: ModelFamily;
  params: Record<string, unknown>;
  /** Hex colour for the model's "body" material; null keeps the model's default. */
  color: string | null;
  rgb: boolean;
  /** "measured" when dimensions come from the component's data, "estimated" when defaults were used. */
  confidence: "measured" | "estimated";
  assumptions: string[];
}

export function layoutFor(spec: ModelSpec): Layout {
  const p = spec.params;
  switch (spec.family) {
    case "gpu": return gpuLayout(p as unknown as GpuParams);
    case "motherboard": return boardLayout(p as unknown as BoardParams);
    case "case": return caseLayout(p as unknown as CaseParams);
    case "air-cooler": return airCoolerLayout(p as unknown as AirCoolerParams);
    case "aio-radiator": return radiatorLayout(p as unknown as RadiatorParams);
    case "aio-pump": return pumpLayout();
    case "ram": return ramLayout(p as unknown as RamParams);
    case "psu": return psuLayout(p as unknown as PsuParams);
    case "fan": return fanLayout(p as unknown as FanParams);
    case "m2": return m2Layout(p as unknown as M2Params);
    case "drive-25": return driveLayout("2.5");
    case "drive-35": return driveLayout("3.5");
  }
}

export interface CatalogModel {
  modelId: string;
  category: string;
  variant: string;
  status: "implemented" | "planned";
  description: string;
  url?: string;
  lod1Url?: string;
  base?: Record<string, unknown>;
  stats?: { triangles: number; bytes: number; lod1Triangles: number; lod1Bytes: number; materials: number };
  baseDimensionsMm?: { x: number; y: number; z: number };
  coverage?: { records: number; weighted: number; current: number } | null;
}

export interface ModelCatalog {
  version: number;
  models: CatalogModel[];
}
