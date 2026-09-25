// Test builds for the 3D lab. Dimensions are typical real-world values for each kind of part; the lab also
// loads real builds from the API (resolved by the backend from OpenDB data).

import type { BuildModels } from "./assembly";
import type { ModelFamily, ModelSpec } from "./spec";

const spec = (modelId: string, family: ModelFamily, params: Record<string, unknown>, extra: Partial<ModelSpec> = {}): ModelSpec => ({
  modelId,
  family,
  params,
  color: null,
  rgb: false,
  confidence: "estimated",
  assumptions: [],
  ...extra,
});

export interface Preset {
  id: string;
  label: string;
  description: string;
  build: BuildModels;
}

const fan120 = spec("fan-120-v1", "fan", { sizeMm: 120, rgb: false });

export const PRESETS: Preset[] = [
  {
    id: "atx-air",
    label: "ATX com air cooler",
    description: "Mid tower, placa de vídeo dual-fan de 304 mm (2,5 slots), cooler torre de 155 mm, 2 pentes com dissipador.",
    build: {
      pcCase: spec("case-atx-mid-tower-v1", "case", { form: "atx-mid", widthMm: 230, heightMm: 494, depthMm: 469, psuShroud: true, glassSide: true, expansionSlots: 7 }),
      motherboard: spec("mb-atx-v1", "motherboard", { form: "atx", ramSlots: 4, m2Slots: 3, x16Slots: 2 }),
      gpu: spec("gpu-dual-fan-v1", "gpu", { lengthMm: 304, heightMm: 118, slots: 2.5, fanCount: 2, fanSizeMm: 100, backplate: true, eightPinConnectors: 2, highPowerConnector: false }),
      cooler: spec("cooler-single-tower-v1", "air-cooler", { style: "single-tower", heightMm: 155, fanSizeMm: 120, fanCount: 1 }),
      memory: spec("ram-heatsink-v1", "ram", { heightMm: 34, heatSpreader: true, rgb: false }),
      memoryModules: 2,
      psu: spec("psu-atx-v1", "psu", { form: "atx", lengthMm: 160, modular: true }),
      storage: [spec("ssd-m2-v1", "m2", { lengthMm: 80 })],
      fans: fan120,
    },
  },
  {
    id: "full-aio",
    label: "Full tower com water cooler 360",
    description: "Full tower branco, placa triple-fan de 336 mm (3,5 slots, conector 16 pinos), radiador 360 no topo, HD de 3,5\".",
    build: {
      pcCase: spec("case-atx-full-tower-v1", "case", { form: "atx-full", widthMm: 268, heightMm: 532, depthMm: 532, psuShroud: true, glassSide: true, expansionSlots: 8 }, { color: "#e9e9e6" }),
      motherboard: spec("mb-atx-v1", "motherboard", { form: "atx", ramSlots: 4, m2Slots: 3, x16Slots: 2 }),
      gpu: spec("gpu-triple-fan-v1", "gpu", { lengthMm: 336, heightMm: 140, slots: 3.5, fanCount: 3, fanSizeMm: 100, backplate: true, eightPinConnectors: 0, highPowerConnector: true }, { color: "#e9e9e6" }),
      cooler: spec("aio-pump-v1", "aio-pump", {}, { rgb: true }),
      radiator: spec("aio-radiator-360-v1", "aio-radiator", { fanCount: 3, fanSizeMm: 120 }, { color: "#e9e9e6" }),
      memory: spec("ram-rgb-v1", "ram", { heightMm: 44, heatSpreader: true, rgb: true }, { rgb: true, color: "#e9e9e6" }),
      memoryModules: 2,
      psu: spec("psu-atx-v1", "psu", { form: "atx", lengthMm: 180, modular: true }),
      storage: [spec("ssd-m2-v1", "m2", { lengthMm: 80 }), spec("hdd-35-v1", "drive-35", {})],
      fans: spec("fan-140-v1", "fan", { sizeMm: 140, rgb: true }, { rgb: true, color: "#e9e9e6" }),
    },
  },
  {
    id: "matx-compact",
    label: "Micro-ATX compacto",
    description: "Gabinete micro-ATX, placa single-fan de 170 mm, cooler baixo de 47 mm, memória sem dissipador.",
    build: {
      pcCase: spec("case-matx-tower-v1", "case", { form: "matx", widthMm: 215, heightMm: 398, depthMm: 407, psuShroud: true, glassSide: true, expansionSlots: 4 }),
      motherboard: spec("mb-matx-v1", "motherboard", { form: "matx", ramSlots: 4, m2Slots: 2, x16Slots: 1 }),
      gpu: spec("gpu-single-fan-v1", "gpu", { lengthMm: 170, heightMm: 112, slots: 2, fanCount: 1, fanSizeMm: 90, backplate: false, eightPinConnectors: 1, highPowerConnector: false }),
      cooler: spec("cooler-low-profile-v1", "air-cooler", { style: "low-profile", heightMm: 47, fanSizeMm: 92, fanCount: 1 }),
      memory: spec("ram-standard-v1", "ram", { heightMm: 31.25, heatSpreader: false, rgb: false }),
      memoryModules: 2,
      psu: spec("psu-atx-v1", "psu", { form: "atx", lengthMm: 140, modular: false }),
      storage: [spec("drive-25-v1", "drive-25", {})],
      fans: fan120,
    },
  },
  {
    id: "conflict",
    label: "Conflito proposital",
    description: "Placa de 340 mm e cooler de 165 mm num gabinete Mini-ITX pequeno: a validação precisa acusar.",
    build: {
      pcCase: spec("case-itx-tower-v1", "case", { form: "itx", widthMm: 180, heightMm: 290, depthMm: 330, psuShroud: false, glassSide: true, expansionSlots: 2 }),
      motherboard: spec("mb-itx-v1", "motherboard", { form: "itx", ramSlots: 2, m2Slots: 1, x16Slots: 1 }),
      gpu: spec("gpu-triple-fan-v1", "gpu", { lengthMm: 340, heightMm: 135, slots: 3, fanCount: 3, fanSizeMm: 95, backplate: true, eightPinConnectors: 3, highPowerConnector: false }),
      cooler: spec("cooler-single-tower-v1", "air-cooler", { style: "single-tower", heightMm: 165, fanSizeMm: 120, fanCount: 1 }),
      memory: spec("ram-rgb-v1", "ram", { heightMm: 44, heatSpreader: true, rgb: true }, { rgb: true }),
      memoryModules: 2,
      psu: spec("psu-sfx-v1", "psu", { form: "sfx", lengthMm: 100, modular: true }),
      storage: [spec("ssd-m2-v1", "m2", { lengthMm: 80 })],
      fans: null,
    },
  },
];
