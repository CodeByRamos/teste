// Catalog of base models. Each entry is a family variant generated from one parametric builder; at runtime a
// single GLB represents every component of that family by re-laying out its parts (see layout.ts).
//
// `coverageKey` links the entry to a family measured in docs/3d/opendb-analysis.json.
// `status: "planned"` entries document the next models without generating anything yet.

import type {
  AirCoolerParams,
  BoardParams,
  CaseParams,
  FanParams,
  GpuParams,
  M2Params,
  PsuParams,
  RadiatorParams,
  RamParams,
} from "../../frontend/src/lib/models3d/layout.ts";

export type Category = "gpu" | "motherboard" | "case" | "cooler" | "aio" | "ram" | "psu" | "fan" | "storage";

interface Base {
  modelId: string;
  category: Category;
  variant: string;
  description: string;
  coverageKey: string;
  /** Parameters the runtime reads from component data (see ModelResolver in the backend). */
  parameters: string[];
  status: "implemented" | "planned";
}

export type ModelDefinition =
  | (Base & { builder: "gpu"; base: GpuParams })
  | (Base & { builder: "motherboard"; base: BoardParams })
  | (Base & { builder: "case"; base: CaseParams })
  | (Base & { builder: "air-cooler"; base: AirCoolerParams })
  | (Base & { builder: "ram"; base: RamParams })
  | (Base & { builder: "psu"; base: PsuParams })
  | (Base & { builder: "fan"; base: FanParams })
  | (Base & { builder: "m2"; base: M2Params })
  | (Base & { builder: "drive"; base: { bay: "2.5" | "3.5" } })
  | (Base & { builder: "radiator"; base: RadiatorParams })
  | (Base & { builder: "pump"; base: Record<string, never> })
  | (Base & { builder: "planned"; base: Record<string, never> });

const GPU_PARAMS = ["lengthMm", "heightMm", "slots", "fanCount", "fanSizeMm", "backplate", "eightPinConnectors", "highPowerConnector", "color"];
const gpu = (variant: string, fanCount: number, lengthMm: number, heightMm: number, slots: number, fanSizeMm: number,
  extra: Partial<GpuParams>, coverageKey: string, description: string): ModelDefinition => ({
  modelId: `gpu-${variant}-v1`, category: "gpu", variant, builder: "gpu", status: "implemented", coverageKey, description,
  parameters: GPU_PARAMS,
  base: { lengthMm, heightMm, slots, fanCount, fanSizeMm, backplate: true, eightPinConnectors: 1, highPowerConnector: false, ...extra },
});

const board = (variant: string, form: BoardParams["form"], ramSlots: number, m2Slots: number, x16Slots: number, coverageKey: string): ModelDefinition => ({
  modelId: `mb-${variant}-v1`, category: "motherboard", variant, builder: "motherboard", status: "implemented", coverageKey,
  description: `Placa-mãe ${form.toUpperCase()}: encaixe, slots de memória e PCIe, dissipadores, I/O e M.2.`,
  parameters: ["form", "ramSlots", "m2Slots", "x16Slots", "color"],
  base: { form, ramSlots, m2Slots, x16Slots },
});

const pcCase = (variant: string, form: CaseParams["form"], widthMm: number, heightMm: number, depthMm: number,
  psuShroud: boolean, expansionSlots: number, coverageKey: string): ModelDefinition => ({
  modelId: `case-${variant}-v1`, category: "case", variant, builder: "case", status: "implemented", coverageKey,
  description: "Gabinete com painéis separados e pontos de montagem para placa-mãe, fonte, fans, radiadores e discos.",
  parameters: ["widthMm", "heightMm", "depthMm", "psuShroud", "glassSide", "expansionSlots", "color"],
  base: { form, widthMm, heightMm, depthMm, psuShroud, glassSide: true, expansionSlots },
});

const planned = (modelId: string, category: Category, variant: string, coverageKey: string, description: string): ModelDefinition => ({
  modelId, category, variant, builder: "planned", status: "planned", coverageKey, description, parameters: [], base: {},
});

export const CATALOG: ModelDefinition[] = [
  // GPU: the three fan layouts cover ~96% of cards in the database.
  gpu("triple-fan", 3, 308, 125, 3, 92, { highPowerConnector: true, eightPinConnectors: 0 }, "gpu-triple-fan",
    "Placa de vídeo com três ventoinhas (maioria das placas intermediárias e topo de linha)."),
  gpu("dual-fan", 2, 245, 120, 2, 95, {}, "gpu-dual-fan",
    "Placa de vídeo com duas ventoinhas (maioria das placas de entrada e intermediárias)."),
  gpu("single-fan", 1, 184, 115, 2, 90, { backplate: false }, "gpu-single-fan",
    "Placa de vídeo compacta com uma ventoinha."),
  planned("gpu-blower-v1", "gpu", "blower", "gpu-blower", "Placa com cooler tipo turbina (blower): corpo fechado, ventoinha radial na ponta."),
  planned("gpu-hybrid-v1", "gpu", "hybrid", "gpu-liquid", "Placa com refrigeração líquida/híbrida: corpo curto + radiador (reaproveita aio-radiator)."),
  planned("gpu-passive-v1", "gpu", "passive", "gpu-passive", "Placa sem ventoinha, só dissipador."),

  // Cases
  pcCase("atx-mid-tower", "atx-mid", 235, 481, 465, true, 7, "case-atx-mid-tower"),
  pcCase("matx-tower", "matx", 215, 398, 407, true, 4, "case-matx-tower"),
  pcCase("atx-full-tower", "atx-full", 268, 532, 532, true, 8, "case-atx-full-tower"),
  pcCase("itx-tower", "itx", 205, 295, 326, false, 2, "case-itx-tower"),
  planned("case-sff-compact-v1", "case", "sff-compact", "case-itx-tower", "Gabinete compacto tipo sanduíche (placa de vídeo com riser)."),
  planned("case-desktop-htpc-v1", "case", "desktop-htpc", "case-desktop-htpc (later)", "Gabinete horizontal de mesa / HTPC."),

  // Motherboards: one builder, four standard sizes.
  board("atx", "atx", 4, 3, 2, "mb-atx"),
  board("matx", "matx", 4, 2, 2, "mb-matx"),
  board("itx", "itx", 2, 1, 1, "mb-itx"),
  board("eatx", "eatx", 4, 3, 3, "mb-eatx"),

  // CPU cooling: AIOs are over half of the cooler records, so radiators are first-class models.
  {
    modelId: "cooler-single-tower-v1", category: "cooler", variant: "single-tower", builder: "air-cooler", status: "implemented",
    coverageKey: "cooler-single-tower", description: "Air cooler torre única com uma ventoinha.",
    parameters: ["heightMm", "fanSizeMm", "fanCount", "color"],
    base: { style: "single-tower", heightMm: 155, fanSizeMm: 120, fanCount: 1 },
  },
  {
    modelId: "cooler-dual-tower-v1", category: "cooler", variant: "dual-tower", builder: "air-cooler", status: "implemented",
    coverageKey: "cooler-dual-tower", description: "Air cooler de torre dupla (duas pilhas de aletas).",
    parameters: ["heightMm", "fanSizeMm", "fanCount", "color"],
    base: { style: "dual-tower", heightMm: 158, fanSizeMm: 120, fanCount: 2 },
  },
  {
    modelId: "cooler-low-profile-v1", category: "cooler", variant: "low-profile", builder: "air-cooler", status: "implemented",
    coverageKey: "cooler-low-profile", description: "Cooler baixo com ventoinha sobre as aletas (gabinetes compactos).",
    parameters: ["heightMm", "fanSizeMm", "color"],
    base: { style: "low-profile", heightMm: 47, fanSizeMm: 92, fanCount: 1 },
  },
  ...[[1, 120, "120"], [2, 120, "240"], [2, 140, "280"], [3, 120, "360"]].map(([fans, size, label]) => ({
    modelId: `aio-radiator-${label}-v1`, category: "aio" as const, variant: `radiator-${label}`, builder: "radiator" as const,
    status: "implemented" as const, coverageKey: `aio-${label}`,
    description: `Radiador de water cooler ${label} mm com ${fans} ventoinha(s).`,
    parameters: ["fanCount", "fanSizeMm", "color"],
    base: { fanCount: fans as number, fanSizeMm: size as number },
  })),
  {
    modelId: "aio-pump-v1", category: "aio", variant: "pump", builder: "pump", status: "implemented", coverageKey: "aio-240",
    description: "Bloco da bomba do water cooler (vai sobre o processador; mangueiras geradas na cena).",
    parameters: ["color"], base: {},
  },
  planned("aio-radiator-420-v1", "aio", "radiator-420", "aio-420", "Radiador 420 mm (3 × 140 mm)."),

  // Memory: one builder; spreader and RGB are flags.
  ...([
    ["heatsink", 34, true, false, "ram-heatsink", "Memória com dissipador."],
    ["rgb", 44, true, true, "ram-rgb", "Memória com dissipador e barra de iluminação RGB."],
    ["standard", 31.25, false, false, "ram-standard", "Memória sem dissipador (chips expostos)."],
  ] as const).map(([variant, heightMm, heatSpreader, rgb, coverageKey, description]) => ({
    modelId: `ram-${variant}-v1`, category: "ram" as const, variant, builder: "ram" as const, status: "implemented" as const,
    coverageKey, description, parameters: ["heightMm", "heatSpreader", "rgb", "modules", "color"],
    base: { heightMm, heatSpreader, rgb },
  })),

  // Power supplies
  ...([["atx", 150, "psu-atx"], ["sfx", 100, "psu-sfx"], ["sfx-l", 130, "psu-sfx-l"]] as const).map(([variant, lengthMm, coverageKey]) => ({
    modelId: `psu-${variant}-v1`, category: "psu" as const, variant, builder: "psu" as const, status: "implemented" as const, coverageKey,
    description: `Fonte ${variant.toUpperCase()}: corpo, grade traseira, ventoinha e painel modular.`,
    parameters: ["lengthMm", "modular", "color"],
    base: { form: variant, lengthMm, modular: true },
  })),

  // Case fans
  ...([120, 140] as const).map((size) => ({
    modelId: `fan-${size}-v1`, category: "fan" as const, variant: `${size}`, builder: "fan" as const, status: "implemented" as const,
    coverageKey: `fan-${size}`, description: `Ventoinha de ${size} mm (moldura, pás, cubo, anel RGB opcional).`,
    parameters: ["sizeMm", "rgb", "color"], base: { sizeMm: size, rgb: false },
  })),
  planned("fan-92-v1", "fan", "92", "fan-92 (later)", "Ventoinha de 92 mm (coolers compactos)."),

  // Storage
  {
    modelId: "ssd-m2-v1", category: "storage", variant: "m2", builder: "m2", status: "implemented", coverageKey: "ssd-m2",
    description: "SSD M.2 (comprimento 2230–22110).", parameters: ["lengthMm"], base: { lengthMm: 80 },
  },
  {
    modelId: "drive-25-v1", category: "storage", variant: "2.5", builder: "drive", status: "implemented", coverageKey: "ssd-sata-25",
    description: "SSD SATA / HD de 2,5\".", parameters: [], base: { bay: "2.5" },
  },
  {
    modelId: "hdd-35-v1", category: "storage", variant: "3.5", builder: "drive", status: "implemented", coverageKey: "hdd-35",
    description: "HD de 3,5\".", parameters: [], base: { bay: "3.5" },
  },
];
