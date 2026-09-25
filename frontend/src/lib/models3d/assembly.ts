// Places every model of a build inside the case using mount points, and checks the result for collisions.
// Pure computation over layouts (no GLB needed), so it runs the same in the browser and in validation scripts.

import * as THREE from "three";
import { caseInterior, type CaseParams, type Layout, type Mount } from "./layout";
import { layoutFor, type ModelSpec } from "./spec";

const MM = 0.001;

export type SlotCategory =
  | "CASE"
  | "MOTHERBOARD"
  | "CPU_COOLER"
  | "MEMORY"
  | "GPU"
  | "POWER_SUPPLY"
  | "STORAGE"
  | "FAN";

export interface BuildModels {
  pcCase: ModelSpec;
  motherboard: ModelSpec;
  gpu?: ModelSpec | null;
  cooler?: ModelSpec | null;
  /** For AIO coolers: the radiator; the pump goes in `cooler`. */
  radiator?: ModelSpec | null;
  memory?: ModelSpec | null;
  memoryModules?: number;
  psu?: ModelSpec | null;
  storage?: ModelSpec[];
  fans?: ModelSpec | null;
}

export interface Placement {
  key: string;
  category: SlotCategory;
  spec: ModelSpec;
  layout: Layout;
  matrix: THREE.Matrix4;
  /** World-space bounds in metres. */
  box: THREE.Box3;
}

export interface Tube {
  from: THREE.Vector3;
  to: THREE.Vector3;
}

export interface Assembly {
  placements: Placement[];
  tubes: Tube[];
  checks: Check[];
}

export interface Check {
  id: string;
  status: "ok" | "warning" | "fail";
  title: string;
  detail: string;
}

function mountMatrix(mount: Mount | undefined): THREE.Matrix4 {
  const m = new THREE.Matrix4();
  if (!mount) return m;
  const rotation = new THREE.Quaternion().setFromEuler(new THREE.Euler(...(mount.rotation ?? [0, 0, 0])));
  return m.compose(new THREE.Vector3(...mount.position.map((v) => v * MM) as [number, number, number]), rotation, new THREE.Vector3(1, 1, 1));
}

function worldBox(layout: Layout, matrix: THREE.Matrix4): THREE.Box3 {
  const local = new THREE.Box3(
    new THREE.Vector3(...layout.bounds.min.map((v) => v * MM) as [number, number, number]),
    new THREE.Vector3(...layout.bounds.max.map((v) => v * MM) as [number, number, number]),
  );
  return local.applyMatrix4(matrix);
}

/** Slots filled first: dual-channel pairs (2nd and 4th slot) on four-slot boards. */
function memorySlots(available: number, modules: number): number[] {
  if (available >= 4 && modules === 2) return [1, 3];
  return Array.from({ length: Math.min(modules, available) }, (_, i) => i);
}

export function assemble(build: BuildModels): Assembly {
  const placements: Placement[] = [];
  const add = (key: string, category: SlotCategory, spec: ModelSpec, matrix: THREE.Matrix4) => {
    const layout = layoutFor(spec);
    const placement = { key, category, spec, layout, matrix, box: worldBox(layout, matrix) };
    placements.push(placement);
    return placement;
  };

  const pcCase = add("case", "CASE", build.pcCase, new THREE.Matrix4());
  const caseMounts = pcCase.layout.mounts;
  const board = add("motherboard", "MOTHERBOARD", build.motherboard, pcCase.matrix.clone().multiply(mountMatrix(caseMounts.motherboard)));
  const boardMounts = board.layout.mounts;
  const onBoard = (mount: string) => board.matrix.clone().multiply(mountMatrix(boardMounts[mount]));

  if (build.cooler) add("cooler", "CPU_COOLER", build.cooler, onBoard("cpu"));
  if (build.memory) {
    const slots = Object.keys(boardMounts).filter((m) => m.startsWith("ram_")).length;
    memorySlots(slots, build.memoryModules ?? 2).forEach((slot) =>
      add(`memory_${slot}`, "MEMORY", build.memory!, onBoard(`ram_${slot}`)));
  }
  if (build.gpu && boardMounts.pcie_x16_0) add("gpu", "GPU", build.gpu, onBoard("pcie_x16_0"));
  if (build.psu) add("psu", "POWER_SUPPLY", build.psu, pcCase.matrix.clone().multiply(mountMatrix(caseMounts.psu)));

  let m2Index = 0;
  let bayIndex = 0;
  for (const [i, drive] of (build.storage ?? []).entries()) {
    if (drive.family === "m2" && boardMounts[`m2_${m2Index}`]) {
      add(`storage_${i}`, "STORAGE", drive, onBoard(`m2_${m2Index++}`));
    } else if (drive.family !== "m2" && bayIndex === 0 && caseMounts.drive_bay_0) {
      bayIndex++;
      add(`storage_${i}`, "STORAGE", drive, pcCase.matrix.clone().multiply(mountMatrix(caseMounts.drive_bay_0)));
    }
  }

  const tubes: Tube[] = [];
  let radiatorAt: "top" | "front" | null = null;
  if (build.radiator) {
    radiatorAt = "top";
    let radiator = add("radiator", "CPU_COOLER", build.radiator, pcCase.matrix.clone().multiply(mountMatrix(caseMounts.radiator_top)));
    if (!pcCase.box.containsBox(radiator.box.clone().expandByScalar(-0.001))) {
      placements.pop();
      radiatorAt = "front";
      radiator = add("radiator", "CPU_COOLER", build.radiator, pcCase.matrix.clone().multiply(mountMatrix(caseMounts.radiator_front)));
    }
    const pump = placements.find((p) => p.key === "cooler");
    if (pump) {
      for (const end of ["tube_in", "tube_out"]) {
        const from = new THREE.Vector3(...pump.layout.mounts[end].position.map((v) => v * MM) as [number, number, number]).applyMatrix4(pump.matrix);
        const to = new THREE.Vector3(...radiator.layout.mounts[end].position.map((v) => v * MM) as [number, number, number]).applyMatrix4(radiator.matrix);
        tubes.push({ from, to });
      }
    }
  }

  if (build.fans) {
    // Typical airflow: front intake + rear exhaust. Top slots are left for radiators.
    const fanMounts = Object.keys(caseMounts).filter((m) =>
      m === "fan_rear" || (m.startsWith("fan_front_") && radiatorAt !== "front"));
    for (const mount of fanMounts) {
      add(mount, "FAN", build.fans, pcCase.matrix.clone().multiply(mountMatrix(caseMounts[mount])));
    }
  }

  return { placements, tubes, checks: check(placements, build.pcCase) };
}

// ---------------------------------------------------------------------------------------------
// Collision and fit checks (visual layer; the compatibility engine remains the authority on compatibility)
// ---------------------------------------------------------------------------------------------

function mm(value: number) {
  return `${Math.round(value / MM)} mm`;
}

function overlap(a: THREE.Box3, b: THREE.Box3): number {
  const x = Math.min(a.max.x, b.max.x) - Math.max(a.min.x, b.min.x);
  const y = Math.min(a.max.y, b.max.y) - Math.max(a.min.y, b.min.y);
  const z = Math.min(a.max.z, b.max.z) - Math.max(a.min.z, b.min.z);
  return x > 0 && y > 0 && z > 0 ? Math.min(x, y, z) : 0;
}

function check(placements: Placement[], caseSpec: ModelSpec): Check[] {
  const interiorMm = caseInterior(caseSpec.params as unknown as CaseParams);
  const interior = new THREE.Box3(
    new THREE.Vector3(...interiorMm.min.map((v) => v * MM) as [number, number, number]),
    new THREE.Vector3(...interiorMm.max.map((v) => v * MM) as [number, number, number]),
  );
  const find = (key: string) => placements.find((p) => p.key === key);
  const all = (category: SlotCategory) => placements.filter((p) => p.category === category);
  const checks: Check[] = [];
  const tolerance = 0.0015;

  /** `throughRear`: the part legitimately passes through rear panel openings (card bracket, PSU inlet). */
  const inside = (placement: Placement | undefined, id: string, title: string, throughRear = false) => {
    if (!placement) return;
    const grown = interior.clone().expandByScalar(tolerance);
    if (throughRear) grown.min.z -= 0.006;
    if (grown.containsBox(placement.box)) {
      checks.push({ id, status: "ok", title, detail: "Dentro do espaço interno do gabinete." });
    } else {
      const over = Math.max(
        placement.box.max.x - interior.max.x, interior.min.x - placement.box.min.x,
        placement.box.max.y - interior.max.y, interior.min.y - placement.box.min.y,
        placement.box.max.z - interior.max.z, interior.min.z - placement.box.min.z,
      );
      checks.push({ id, status: "fail", title, detail: `Passa ${mm(over)} do espaço interno do gabinete.` });
    }
  };
  inside(find("motherboard"), "fit.motherboard", "Placa-mãe no gabinete");
  inside(find("gpu"), "fit.gpu", "Placa de vídeo no gabinete", true);
  inside(find("psu"), "fit.psu", "Fonte no gabinete", true);
  const cooler = find("cooler");
  inside(cooler, "fit.cooler", "Cooler até a tampa lateral");
  if (cooler) {
    const side = interior.max.x - cooler.box.max.x;
    if (side >= 0) checks.push({ id: "clearance.cooler", status: side < 0.008 ? "warning" : "ok", title: "Folga do cooler", detail: `${mm(side)} até a tampa lateral.` });
  }
  inside(find("radiator"), "fit.radiator", "Radiador no gabinete");

  const pair = (a: Placement | undefined, b: Placement | undefined, id: string, title: string, status: "warning" | "fail") => {
    if (!a || !b) return;
    const depth = overlap(a.box, b.box);
    checks.push(depth > tolerance
      ? { id, status, title, detail: `As peças se sobrepõem em cerca de ${mm(depth)}.` }
      : { id, status: "ok", title, detail: "Sem contato entre as peças." });
  };
  const gpu = find("gpu");
  pair(gpu, cooler, "collision.gpu-cooler", "Placa de vídeo × cooler", "fail");
  pair(gpu, find("psu"), "collision.gpu-psu", "Placa de vídeo × fonte", "fail");
  for (const drive of all("STORAGE")) {
    if (drive.spec.family !== "m2") pair(gpu, drive, "collision.gpu-drive", "Placa de vídeo × disco", "fail");
  }
  if (cooler && cooler.spec.family === "air-cooler") {
    for (const stick of all("MEMORY")) {
      pair(stick, cooler, "collision.ram-cooler", "Memória × cooler", "warning");
    }
  }
  const radiator = find("radiator");
  for (const stick of all("MEMORY")) pair(stick, radiator, "collision.ram-radiator", "Memória × radiador", "warning");
  pair(find("motherboard"), radiator, "collision.board-radiator", "Placa-mãe × radiador", "warning");

  // Deduplicate identical outcomes for repeated parts (each memory module).
  const seen = new Set<string>();
  return checks.filter((c) => {
    const key = `${c.id}|${c.status}|${c.detail}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
