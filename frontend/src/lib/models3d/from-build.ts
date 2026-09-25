// Turns a build from the API (items with backend-resolved model specs) into the models the 3D scene assembles.

import type { BuildItem, Category, ModelSpecDto } from "../types";
import type { BuildModels, SlotCategory } from "./assembly";
import { COLOR_NAMES } from "./rig";
import type { ModelCatalog, ModelFamily, ModelSpec } from "./spec";

let catalogPromise: Promise<ModelCatalog> | null = null;

export function loadModelCatalog(): Promise<ModelCatalog> {
  catalogPromise ??= fetch("/3d-models/catalog.json").then((response) => {
    if (!response.ok) throw new Error("catalog unavailable");
    return response.json();
  });
  return catalogPromise;
}

/** Complete a spec with the base model's defaults (the backend only sends values it knows). */
function complete(dto: ModelSpecDto, catalog: ModelCatalog): ModelSpec {
  const base = catalog.models.find((model) => model.modelId === dto.modelId)?.base ?? {};
  return {
    modelId: dto.modelId,
    family: dto.family as ModelFamily,
    params: { ...base, ...dto.params },
    color: dto.color ? COLOR_NAMES[dto.color] ?? null : null,
    rgb: dto.rgb,
    confidence: dto.confidence,
    assumptions: dto.assumptions,
  };
}

/** Null when the build lacks a case or motherboard: there is nothing to mount parts into. */
export function toBuildModels(items: BuildItem[], catalog: ModelCatalog): BuildModels | null {
  const specs = (category: Category) =>
    items.filter((item) => item.category === category).flatMap((item) => (item.models ?? []).map((dto) => complete(dto, catalog)));
  const one = (category: Category) => specs(category)[0] ?? null;

  const pcCase = one("CASE");
  const motherboard = one("MOTHERBOARD");
  if (!pcCase || !motherboard) return null;

  const cooling = specs("CPU_COOLER");
  const radiator = cooling.find((s) => s.family === "aio-radiator") ?? null;
  const cooler = cooling.find((s) => s.family === "aio-pump" || s.family === "air-cooler") ?? null;
  const memory = one("MEMORY");
  const fan = catalog.models.find((m) => m.modelId === "fan-120-v1");

  return {
    pcCase,
    motherboard,
    gpu: one("GPU"),
    cooler,
    radiator,
    memory,
    memoryModules: typeof memory?.params.modules === "number" ? (memory.params.modules as number) : 2,
    psu: one("POWER_SUPPLY"),
    storage: specs("STORAGE"),
    // Case fans are not part of the build data; the case gets typical intake/exhaust fans.
    fans: fan
      ? { modelId: "fan-120-v1", family: "fan", params: { ...fan.base }, color: pcCase.color, rgb: false, confidence: "estimated", assumptions: [] }
      : null,
  };
}

const CATEGORY_OF_SLOT: Record<SlotCategory, Category | null> = {
  CASE: "CASE",
  MOTHERBOARD: "MOTHERBOARD",
  CPU_COOLER: "CPU_COOLER",
  MEMORY: "MEMORY",
  GPU: "GPU",
  POWER_SUPPLY: "POWER_SUPPLY",
  STORAGE: "STORAGE",
  FAN: null,
};

export function categoryOfSlot(slot: SlotCategory): Category | null {
  return CATEGORY_OF_SLOT[slot];
}
