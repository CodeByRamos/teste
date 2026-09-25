// PBR materials (glTF metallic-roughness). Materials are shared by *role*: the runtime recolours a model by
// role (e.g. "body" becomes white for a white card) instead of knowing individual meshes.

export interface MaterialSpec {
  name: string;
  baseColor: [number, number, number, number];
  metallic: number;
  roughness: number;
  emissive?: [number, number, number];
  alphaBlend?: boolean;
  doubleSided?: boolean;
}

const rgb = (hex: string, alpha = 1): [number, number, number, number] => {
  const n = parseInt(hex.slice(1), 16);
  // glTF base colours are linear; convert from sRGB so authored hex values look as intended.
  const lin = (c: number) => {
    const v = c / 255;
    return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return [lin((n >> 16) & 255), lin((n >> 8) & 255), lin(n & 255), alpha];
};

export const MATERIALS = {
  /** Main visible shell of a part; recoloured at runtime from the component's colour. */
  body: { name: "body", baseColor: rgb("#1d1f22"), metallic: 0.15, roughness: 0.55 },
  /** Secondary trim (shroud accents, heatsink edges). */
  accent: { name: "accent", baseColor: rgb("#3a3d42"), metallic: 0.6, roughness: 0.4 },
  metal: { name: "metal", baseColor: rgb("#b8bcc2"), metallic: 1, roughness: 0.32 },
  aluminium: { name: "aluminium", baseColor: rgb("#c9ccd1"), metallic: 1, roughness: 0.42 },
  dark: { name: "dark", baseColor: rgb("#0e0f11"), metallic: 0.1, roughness: 0.7 },
  pcb: { name: "pcb", baseColor: rgb("#15181b"), metallic: 0.05, roughness: 0.6 },
  pcbGreen: { name: "pcb-green", baseColor: rgb("#1c3b2b"), metallic: 0.05, roughness: 0.6 },
  plasticWhite: { name: "plastic-white", baseColor: rgb("#e7e7e4"), metallic: 0, roughness: 0.5 },
  connector: { name: "connector", baseColor: rgb("#2a2c30"), metallic: 0, roughness: 0.6 },
  copper: { name: "copper", baseColor: rgb("#b87333"), metallic: 1, roughness: 0.3 },
  blade: { name: "blade", baseColor: rgb("#141517"), metallic: 0, roughness: 0.45, doubleSided: true },
  sticker: { name: "sticker", baseColor: rgb("#5b5f66"), metallic: 0, roughness: 0.8 },
  mesh: { name: "mesh", baseColor: rgb("#16171a"), metallic: 0.3, roughness: 0.85 },
  glass: { name: "glass", baseColor: rgb("#9fb3c8", 0.14), metallic: 0, roughness: 0.05, alphaBlend: true, doubleSided: true },
  rgb: { name: "rgb", baseColor: rgb("#ffffff"), metallic: 0, roughness: 0.4, emissive: [0.45, 0.55, 1.0] },
} satisfies Record<string, MaterialSpec>;

export type MaterialKey = keyof typeof MATERIALS;
