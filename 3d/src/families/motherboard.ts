import { boardLayout, type BoardParams } from "../../../frontend/src/lib/models3d/layout.ts";
import { at, box, finStack, roundedBox, strips, studGrid, type Detail } from "../kit/geometry.ts";
import type { ModelDraft, Primitive } from "../kit/export.ts";

/**
 * Motherboard. The elements that define how a board reads at a glance are modelled — socket, RAM and PCIe
 * slots, VRM and chipset heatsinks, I/O cover, main power connectors, M.2 slots — and nothing smaller.
 */
export function buildMotherboard(modelId: string, variant: string, params: BoardParams, detail: Detail): ModelDraft {
  const layout = boardLayout(params);
  const P = layout.parts;
  const parts: Record<string, Primitive[]> = {};
  const size = (name: string) => P[name].size;

  const [pt, ph, pd] = size("pcb");
  parts.pcb = [
    { geometry: box(pt, ph, pd), material: "pcb" },
    // Faint traces so the board does not read as a flat slab.
    { geometry: at(strips([0.2, ph * 0.3, pd * 0.35], "y", 10, 0.7), pt / 2 + 0.05, -ph * 0.15, pd * 0.2), material: "accent" },
  ];

  const [ix, iy, iz] = size("io_cover");
  parts.io_cover = [{ geometry: roundedBox(ix, iy, iz, 3), material: "accent" }];
  const [sx, sy, sz] = size("io_shield");
  parts.io_shield = [
    { geometry: box(sz, sy, sx).rotateY(Math.PI / 2), material: "metal" },
    { geometry: at(studGrid(sx * 0.6, sy * 0.8, 2, 7, 9, 1.4, "z"), 0, 0, -0.6), material: "dark" },
  ];

  const [kx, ky, kz] = size("socket");
  parts.socket = [{ geometry: box(kx, ky, kz), material: "metal" }];
  parts.cpu = [{ geometry: roundedBox(...size("cpu"), 1.5), material: "aluminium" }];

  const vrmFins = (name: string, axis: "y" | "z") => {
    const [x, y, z] = size(name);
    return [
      { geometry: roundedBox(x * 0.35, y, z, 1.5).translate(-x * 0.32, 0, 0), material: "accent" as const },
      { geometry: finStack([x * 0.62, y, z], axis, detail === "high" ? 3 : 6, 1.2).translate(x * 0.18, 0, 0), material: "accent" as const },
    ];
  };
  parts.vrm_top = vrmFins("vrm_top", "z");
  parts.vrm_rear = vrmFins("vrm_rear", "y");

  parts.eps_connector = [{ geometry: box(...size("eps_connector")), material: "connector" }];
  parts.atx_connector = [{ geometry: box(...size("atx_connector")), material: "connector" }];
  parts.sata_ports = [{ geometry: box(...size("sata_ports")), material: "connector" }];
  parts.chipset = [{ geometry: roundedBox(...size("chipset"), 2), material: "accent" }];

  for (const name of Object.keys(P)) {
    if (name.startsWith("ram_slot_")) {
      const [x, y, z] = size(name);
      parts[name] = [
        { geometry: box(x, y, z), material: "dark" },
        { geometry: at(box(x + 1.5, 6, z + 1), 0.5, y / 2 - 3, 0), material: "connector" },
        { geometry: at(box(x + 1.5, 6, z + 1), 0.5, -y / 2 + 3, 0), material: "connector" },
      ];
    }
    if (name.startsWith("pcie_x16_")) {
      const [x, y, z] = size(name);
      parts[name] = [
        { geometry: box(x, y, z), material: "dark" },
        // Metal reinforcement that full-length slots have on modern boards.
        { geometry: box(x + 0.6, y + 0.8, z * 0.96), material: "metal" },
      ];
    }
    if (name.startsWith("m2_slot_")) {
      parts[name] = [{ geometry: box(...size(name)), material: "connector" }];
    }
  }
  return { modelId, family: "motherboard", variant, baseParams: { ...params }, layout, parts };
}
