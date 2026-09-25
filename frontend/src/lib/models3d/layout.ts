// Parametric layout of every 3D model family.
//
// This module is the single source of truth for proportions. It is used twice:
//   - by the generator (3d/src/build.ts) to place parts when baking a family's base GLB;
//   - at runtime to adapt that GLB to a real component (e.g. a 304 mm dual-fan card) without distorting it:
//     each part gets its own target size, so bodies stretch, fans keep their round shape, connectors keep
//     their real size, and thickness follows the slot count.
//
// Rules for this file: pure functions, millimetres, no imports, erasable TypeScript only (it is executed
// directly by Node's type stripping in the generator).
//
// Conventions
//   - A part's geometry is authored centred at its origin with its base size. `position` is the part centre,
//     `size` its target extent on each axis. Runtime scale = size / baseSize, per axis.
//   - Mount points are where other models attach; each model family documents its local axes below.

export type Vec3 = [number, number, number];

export interface PartLayout {
  position: Vec3;
  size: Vec3;
  visible?: boolean;
}

export interface Mount {
  position: Vec3;
  /** Euler XYZ in radians, applied to the attached model. */
  rotation?: Vec3;
}

export interface Layout {
  parts: Record<string, PartLayout>;
  mounts: Record<string, Mount>;
  /** Axis-aligned bounds of the whole model in its local frame, used for collision checks. */
  bounds: { min: Vec3; max: Vec3 };
}

export const SLOT_PITCH_MM = 20.32;

const part = (position: Vec3, size: Vec3, visible = true): PartLayout => ({ position, size, visible });
const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

// =============================================================================================
// GPU
// Local frame: origin at the bracket plane (z = 0) on the slot line, at the card edge that plugs into the
// motherboard. +Z along the card length, +X card height (away from the motherboard), -Y thickness (fans face
// down), +Y backplate side.
// =============================================================================================

export interface GpuParams {
  lengthMm: number;
  heightMm: number;
  /** Slots the card occupies (2, 2.5, 3, 3.5, 4). */
  slots: number;
  fanCount: number;
  fanSizeMm: number;
  backplate: boolean;
  /** Number of 8-pin plugs (0 for 16-pin cards, which set `highPowerConnector`). */
  eightPinConnectors: number;
  highPowerConnector: boolean;
}

export function gpuLayout(p: GpuParams): Layout {
  const L = clamp(p.lengthMm, 120, 420);
  const H = clamp(p.heightMm, 60, 170);
  const bracketSlots = Math.ceil(p.slots - 0.01);
  const T = clamp(p.slots * SLOT_PITCH_MM - 3, 16, 90);
  const shroudT = T - 4;
  const fanZone = L - 24;
  const spacing = fanZone / Math.max(1, p.fanCount);
  const fan = Math.min(p.fanSizeMm, spacing - 3, H - 10);
  const parts: Record<string, PartLayout> = {
    bracket: part([60, -(bracketSlots * SLOT_PITCH_MM) / 2 + 4, -0.6], [120, bracketSlots * SLOT_PITCH_MM - 2, 1.2]),
    pcb: part([(H - 6) / 2, -0.8, 6 + (L - 12) / 2], [H - 6, 1.6, L - 12]),
    backplate: part([(H - 4) / 2, 2.2, 4 + (L - 8) / 2], [H - 4, 2, L - 8], p.backplate),
    shroud: part([H / 2, -1.6 - shroudT / 2, L / 2], [H, shroudT, L]),
    pcie_fingers: part([-4, -0.8, 42 + 44.5], [8, 1.4, 89]),
  };
  for (let i = 0; i < p.fanCount; i++) {
    parts[`fan_${i}`] = part([H / 2, -1.6 - shroudT + 5, 12 + spacing * (i + 0.5)], [fan, 10, fan]);
  }
  const plugs = p.highPowerConnector ? 1 : clamp(p.eightPinConnectors, 0, 3);
  parts.power_connector = part([H + 5, -6, L * 0.62], [10, 12, (p.highPowerConnector ? 20 : 22) * Math.max(1, plugs)], plugs > 0);
  return { parts, mounts: {}, bounds: { min: [-8, -1.6 - shroudT, -1.2], max: [H + 10, 4, L] } };
}

// =============================================================================================
// Motherboard
// Local frame: origin at the board's rear-top corner on its back face. +X towards the components (away from
// the case tray), +Z towards the front edge, -Y downwards. Mounts: CPU (top of the heat spreader), each RAM
// slot, each x16 slot (at the bracket plane of a card installed there), M.2 slots.
// =============================================================================================

export type BoardForm = "atx" | "matx" | "itx" | "eatx";

export interface BoardParams {
  form: BoardForm;
  ramSlots: number;
  m2Slots: number;
  x16Slots: number;
}

export const BOARD_SIZE: Record<BoardForm, { depth: number; height: number }> = {
  atx: { depth: 244, height: 305 },
  matx: { depth: 244, height: 244 },
  itx: { depth: 170, height: 170 },
  eatx: { depth: 330, height: 305 },
};

export function boardLayout(p: BoardParams): Layout {
  const { depth: D, height: H } = BOARD_SIZE[p.form];
  const itx = p.form === "itx";
  const cpu = { y: itx ? -72 : -88, z: itx ? 70 : 112 };
  const pcb = 1.6;
  const parts: Record<string, PartLayout> = {
    pcb: part([pcb / 2, -H / 2, D / 2], [pcb, H, D]),
    io_cover: part([pcb + 15, -(4 + 78), 15], [30, 156, 30]),
    io_shield: part([20, -79.5, -2], [44.5, 158.8, 1]),
    socket: part([pcb + 2.5, cpu.y, cpu.z], [5, 58, 58]),
    cpu: part([pcb + 7, cpu.y, cpu.z], [4, 40, 40]),
    vrm_top: part([pcb + 11, -16, cpu.z - 6], [22, 18, itx ? 70 : 96]),
    vrm_rear: part([pcb + 11, cpu.y - 4, 30 + 12], [22, itx ? 80 : 110, 20]),
    eps_connector: part([pcb + 7, -8, 58], [14, 10, 18]),
    atx_connector: part([pcb + 8, -H * 0.4, D - 8], [16, 52, 12]),
    sata_ports: part([pcb + 7, -H + (itx ? 30 : 55), D - 7], [14, 30, 10], !itx),
    chipset: part([pcb + 4, itx ? -150 : -H + 60, D - (itx ? 30 : 55)], [8, 42, 42], !itx),
  };
  const mounts: Record<string, Mount> = {
    cpu: { position: [pcb + 9, cpu.y, cpu.z] },
  };

  const ram = clamp(p.ramSlots, 1, 4);
  for (let i = 0; i < ram; i++) {
    const z = cpu.z + (itx ? 44 : 52) + i * 9.5;
    parts[`ram_slot_${i}`] = part([pcb + 4.5, -18 - 66.7, z], [9, 133.4, 6.5]);
    mounts[`ram_${i}`] = { position: [pcb + 9, -18 - 66.7, z] };
  }

  const x16 = clamp(p.x16Slots, 1, itx ? 1 : 3);
  const firstSlotY = itx ? -148 : -155;
  for (let i = 0; i < x16; i++) {
    const y = firstSlotY - i * 3 * SLOT_PITCH_MM;
    if (y < -H + 12) break;
    parts[`pcie_x16_${i}`] = part([pcb + 5.5, y, 28 + 44.5], [11, 7, 89]);
    mounts[`pcie_x16_${i}`] = { position: [pcb + 11, y, -14] };
  }

  const m2 = clamp(p.m2Slots, 0, 3);
  const m2Rows = [firstSlotY + 28, firstSlotY - 40, firstSlotY - 101];
  for (let i = 0; i < m2; i++) {
    const y = m2Rows[i];
    if (y < -H + 15) break;
    parts[`m2_slot_${i}`] = part([pcb + 2, y, 40], [4, 22, 6]);
    mounts[`m2_${i}`] = { position: [pcb + 3.2, y, 40 + 3 + 40] };
  }
  return { parts, mounts, bounds: { min: [-2, -H, -3], max: [45, 0, D] } };
}

// =============================================================================================
// Case
// Local frame: origin at the bottom centre. X across the width (tray side at -X, window at +X), +Y up,
// Z from rear (-D/2) to front (+D/2). Mounts: motherboard, PSU, fans, radiators, drive bays.
// =============================================================================================

export type CaseForm = "atx-mid" | "atx-full" | "matx" | "itx";

export interface CaseParams {
  form: CaseForm;
  widthMm: number;
  heightMm: number;
  depthMm: number;
  psuShroud: boolean;
  glassSide: boolean;
  expansionSlots: number;
}

export const CASE_DEFAULTS: Record<CaseForm, { width: number; height: number; depth: number; slots: number }> = {
  "atx-mid": { width: 235, height: 481, depth: 465, slots: 7 },
  "atx-full": { width: 268, height: 532, depth: 532, slots: 8 },
  matx: { width: 215, height: 398, depth: 407, slots: 4 },
  itx: { width: 205, height: 295, depth: 326, slots: 2 },
};

export const FOOT_HEIGHT_MM = 10;
const PANEL = 1.5;
const FRONT_DEPTH = 18;
const BOARD_TOP_MARGIN = 28;
const BOARD_REAR_MARGIN = 14;
const TRAY_STANDOFF = 8;

export function caseLayout(p: CaseParams): Layout {
  const W = clamp(p.widthMm, 150, 340);
  const H = clamp(p.heightMm, 250, 700);
  const D = clamp(p.depthMm, 250, 700);
  const floor = FOOT_HEIGHT_MM + PANEL;
  const shroudH = p.psuShroud ? 100 : 0;
  const trayX = -W / 2 + PANEL + TRAY_STANDOFF;
  const parts: Record<string, PartLayout> = {
    tray_panel: part([-W / 2 + PANEL / 2, H / 2 + FOOT_HEIGHT_MM / 2, 0], [PANEL, H - FOOT_HEIGHT_MM, D]),
    side_panel: part([W / 2 - 2, H / 2 + FOOT_HEIGHT_MM / 2, 0], [4, H - FOOT_HEIGHT_MM - 8, D - 8], true),
    top_panel: part([0, H - 1, 0], [W, 2, D]),
    bottom_panel: part([0, FOOT_HEIGHT_MM + 1, 0], [W, 2, D]),
    front_panel: part([0, H / 2 + FOOT_HEIGHT_MM / 2, D / 2 - FRONT_DEPTH / 2], [W, H - FOOT_HEIGHT_MM, FRONT_DEPTH]),
    rear_panel: part([0, H / 2 + FOOT_HEIGHT_MM / 2, -D / 2 + PANEL / 2], [W, H - FOOT_HEIGHT_MM, PANEL]),
    psu_shroud: part([2, floor + shroudH / 2, -FRONT_DEPTH / 2], [W - 6, shroudH, D - FRONT_DEPTH - 40], p.psuShroud),
    expansion_slots: part(
      [trayX + 60, 0, -D / 2 + PANEL + 1],
      [110, p.expansionSlots * SLOT_PITCH_MM, 2],
    ),
    foot_0: part([-W / 2 + 25, FOOT_HEIGHT_MM / 2, -D / 2 + 30], [30, FOOT_HEIGHT_MM, 30]),
    foot_1: part([W / 2 - 25, FOOT_HEIGHT_MM / 2, -D / 2 + 30], [30, FOOT_HEIGHT_MM, 30]),
    foot_2: part([-W / 2 + 25, FOOT_HEIGHT_MM / 2, D / 2 - 30], [30, FOOT_HEIGHT_MM, 30]),
    foot_3: part([W / 2 - 25, FOOT_HEIGHT_MM / 2, D / 2 - 30], [30, FOOT_HEIGHT_MM, 30]),
  };
  const boardTop = H - BOARD_TOP_MARGIN;
  // Expansion slot covers line up with the motherboard's first x16 slot, in whole slot steps.
  parts.expansion_slots.position[1] = boardTop - 155 - (p.expansionSlots * SLOT_PITCH_MM) / 2 + SLOT_PITCH_MM / 2;

  const mounts: Record<string, Mount> = {
    motherboard: { position: [trayX, boardTop, -D / 2 + BOARD_REAR_MARGIN] },
    psu: { position: [-W / 2 + PANEL + 2 + 75, floor, -D / 2 + PANEL] },
    fan_rear: { position: [trayX + 75, boardTop - 70, -D / 2 + PANEL + 13] },
  };
  const frontZ = D / 2 - FRONT_DEPTH - 13;
  const frontFans = clamp(Math.floor((H - floor - 40) / 125), 1, 3);
  for (let i = 0; i < frontFans; i++) {
    mounts[`fan_front_${i}`] = { position: [0, floor + 20 + 62.5 + i * 125, frontZ] };
  }
  const topFans = clamp(Math.floor((D - FRONT_DEPTH - 60) / 125), 1, 3);
  for (let i = 0; i < topFans; i++) {
    mounts[`fan_top_${i}`] = { position: [10, H - 2 - 13, -D / 2 + 40 + 62.5 + i * 125], rotation: [Math.PI / 2, 0, 0] };
  }
  mounts.radiator_top = { position: [10, H - 2 - 13 - 25, -D / 2 + 40 + (topFans * 125) / 2], rotation: [0, 0, 0] };
  mounts.radiator_front = { position: [0, floor + 20 + (frontFans * 125) / 2, frontZ - 25], rotation: [Math.PI / 2, 0, 0] };
  mounts.drive_bay_0 = { position: [0, floor + 14, D / 2 - FRONT_DEPTH - 100] };
  return { parts, mounts, bounds: { min: [-W / 2, 0, -D / 2], max: [W / 2, H, D / 2] } };
}

/** Free space inside the case, for collision checks (excludes panels and the front intake zone). */
export function caseInterior(p: CaseParams): { min: Vec3; max: Vec3 } {
  const W = clamp(p.widthMm, 150, 340);
  const H = clamp(p.heightMm, 250, 700);
  const D = clamp(p.depthMm, 250, 700);
  return {
    min: [-W / 2 + PANEL, FOOT_HEIGHT_MM + PANEL, -D / 2 + PANEL],
    max: [W / 2 - 4, H - 2, D / 2 - FRONT_DEPTH],
  };
}

// =============================================================================================
// CPU air cooler
// Local frame: origin at the top of the CPU heat spreader. +X away from the motherboard (cooler height),
// Y across the fins (fan width), +Z towards the case front (airflow runs from +Z to -Z).
// =============================================================================================

export type CoolerStyle = "single-tower" | "dual-tower" | "low-profile";

export interface AirCoolerParams {
  style: CoolerStyle;
  heightMm: number;
  fanSizeMm: number;
  fanCount: number;
}

export function airCoolerLayout(p: AirCoolerParams): Layout {
  const Hc = clamp(p.heightMm, 30, 175);
  const fan = clamp(p.fanSizeMm, 80, 140);
  const parts: Record<string, PartLayout> = {
    base: part([4, 0, 0], [8, 42, 42]),
  };
  if (p.style === "low-profile") {
    const finH = Math.max(10, Hc - 8 - 15);
    parts.fin_stack_0 = part([8 + finH / 2, 0, 0], [finH, fan, fan]);
    parts.fan_0 = part([8 + finH + 7.5, 0, 0], [15, fan, fan]);
    return { parts, mounts: {}, bounds: { min: [0, -fan / 2, -fan / 2], max: [Hc, fan / 2, fan / 2] } };
  }
  const towers = p.style === "dual-tower" ? 2 : 1;
  const stackDepth = towers === 2 ? 48 : 52;
  const gap = towers === 2 ? 26 : 0;
  const finH = Hc - 30;
  const totalDepth = towers * stackDepth + gap;
  parts.heatpipes = part([6 + (Hc - 16) / 2, 0, 0], [Hc - 16, fan * 0.55, totalDepth * 0.85]);
  for (let t = 0; t < towers; t++) {
    const z = -totalDepth / 2 + stackDepth / 2 + t * (stackDepth + gap);
    parts[`fin_stack_${t}`] = part([28 + finH / 2, 0, z], [finH, fan + 2, stackDepth]);
    parts[`top_cover_${t}`] = part([28 + finH + 1.5, 0, z], [3, fan + 2, stackDepth]);
  }
  const fanX = Hc - fan / 2 - 2;
  const fanPositions = towers === 2 ? [0, totalDepth / 2 + 13] : [totalDepth / 2 + 13, -totalDepth / 2 - 13];
  const fans = clamp(p.fanCount, 1, 2);
  for (let i = 0; i < fans; i++) {
    parts[`fan_${i}`] = part([fanX, 0, fanPositions[i]], [fan, fan, 25]);
  }
  const zMax = Math.max(totalDepth / 2, ...fanPositions.slice(0, fans).map((z) => z + 12.5));
  const zMin = Math.min(-totalDepth / 2, ...fanPositions.slice(0, fans).map((z) => z - 12.5));
  return { parts, mounts: {}, bounds: { min: [0, -fan / 2 - 1, zMin], max: [Hc, fan / 2 + 1, zMax] } };
}

// =============================================================================================
// RAM module
// Local frame: origin at the top of the motherboard slot, centred. +X module height, Y along the module
// length, Z thickness.
// =============================================================================================

export interface RamParams {
  heightMm: number;
  heatSpreader: boolean;
  rgb: boolean;
}

export function ramLayout(p: RamParams): Layout {
  const hasSpreader = p.heatSpreader || p.rgb;
  const H = clamp(p.heightMm, 30, 60);
  const pcbH = 31.25;
  const T = hasSpreader ? 7 : 3;
  return {
    parts: {
      pcb: part([pcbH / 2, 0, 0], [pcbH, 133.35, 1.2]),
      chips: part([pcbH / 2 + 2, 0, 0], [9, 112, 2.6], !hasSpreader),
      heatspreader: part([(H - (p.rgb ? 5 : 0)) / 2, 0, 0], [H - (p.rgb ? 5 : 0), 133.35, T], hasSpreader),
      rgb_bar: part([H - 2.5, 0, 0], [5, 128, T - 1], p.rgb),
    },
    mounts: {},
    bounds: { min: [0, -66.7, -T / 2], max: [hasSpreader ? H : pcbH, 66.7, T / 2] },
  };
}

// =============================================================================================
// Power supply
// Local frame: origin at the centre of the rear face's bottom edge. X width, +Y height, +Z into the case.
// =============================================================================================

export type PsuForm = "atx" | "sfx" | "sfx-l";

export const PSU_SIZE: Record<PsuForm, { width: number; height: number; length: number }> = {
  atx: { width: 150, height: 86, length: 150 },
  sfx: { width: 125, height: 63.5, length: 100 },
  "sfx-l": { width: 125, height: 63.5, length: 130 },
};

export interface PsuParams {
  form: PsuForm;
  lengthMm: number;
  modular: boolean;
}

export function psuLayout(p: PsuParams): Layout {
  const { width: W, height: H } = PSU_SIZE[p.form];
  const L = clamp(p.lengthMm, 90, 260);
  const fan = Math.min(W, L) - (p.form === "atx" ? 18 : 12);
  return {
    parts: {
      body: part([0, H / 2, L / 2], [W, H, L]),
      rear_grille: part([0, H / 2, -0.4], [W - 8, H - 8, 1]),
      power_inlet: part([W / 2 - 28, H - 20, -2], [30, 22, 4]),
      fan_grille: part([0, -0.6, L / 2], [fan, 1.4, fan]),
      modular_panel: part([0, H / 2, L + 0.5], [W - 24, H - 22, 1.2], p.modular),
      label: part([W / 2 + 0.3, H / 2, L / 2], [0.6, H - 26, L - 40]),
    },
    mounts: {},
    bounds: { min: [-W / 2, -1.5, -3], max: [W / 2, H, L + 1] },
  };
}

// =============================================================================================
// Case fan
// Local frame: centred, airflow along Z.
// =============================================================================================

export interface FanParams {
  sizeMm: number;
  rgb: boolean;
}

export function fanLayout(p: FanParams): Layout {
  const S = clamp(p.sizeMm, 80, 200);
  return {
    parts: {
      frame: part([0, 0, 0], [S, S, 25]),
      rotor: part([0, 0, 0], [S - 10, S - 10, 18]),
      hub: part([0, 0, 0], [S * 0.34, S * 0.34, 20]),
      rgb_ring: part([0, 0, 11.5], [S - 6, S - 6, 2], p.rgb),
    },
    mounts: {},
    bounds: { min: [-S / 2, -S / 2, -12.5], max: [S / 2, S / 2, 12.5] },
  };
}

// =============================================================================================
// Storage
// M.2: origin at the drive centre lying on the board: X thickness (away from the board), Y width, Z length.
// 2.5" / 3.5": centred, lying flat: X width, Y thickness, Z length.
// =============================================================================================

export interface M2Params {
  lengthMm: number;
}

export function m2Layout(p: M2Params): Layout {
  const L = clamp(p.lengthMm, 30, 110);
  return {
    parts: {
      pcb: part([0.4, 0, 0], [0.8, 22, L]),
      nand: part([1.5, 0, L * 0.12], [1.4, 18, L * 0.55]),
      controller: part([1.5, 0, -L / 2 + 12], [1.4, 12, 12]),
      label: part([2.25, 0, L * 0.08], [0.1, 20, L * 0.7]),
    },
    mounts: {},
    bounds: { min: [0, -11, -L / 2], max: [2.3, 11, L / 2] },
  };
}

export type DriveBay = "2.5" | "3.5";

export const DRIVE_SIZE: Record<DriveBay, { width: number; thickness: number; length: number }> = {
  "2.5": { width: 69.85, thickness: 7, length: 100 },
  "3.5": { width: 101.6, thickness: 26.1, length: 147 },
};

export function driveLayout(bay: DriveBay): Layout {
  const { width: W, thickness: T, length: L } = DRIVE_SIZE[bay];
  return {
    parts: {
      body: part([0, 0, 0], [W, T, L]),
      label: part([0, T / 2 + 0.05, 8], [W * 0.8, 0.1, L * 0.6]),
      connector: part([W * 0.2, 0, -L / 2 - 1], [W * 0.45, Math.min(T - 1, 6), 2]),
    },
    mounts: {},
    bounds: { min: [-W / 2, -T / 2, -L / 2 - 2], max: [W / 2, T / 2, L / 2] },
  };
}

// =============================================================================================
// AIO liquid cooler
// Radiator: centred; Z along the radiator length, X across, fans on the -Y face (radiator above its fans when
// top-mounted). Pump: origin at the top of the CPU heat spreader, +X away from the motherboard.
// =============================================================================================

export interface RadiatorParams {
  fanCount: number;
  fanSizeMm: number;
}

export function radiatorLayout(p: RadiatorParams): Layout {
  const fan = p.fanSizeMm;
  const core = p.fanCount * fan + 4;
  const tank = 18;
  const L = core + 2 * tank;
  const W = fan + 4;
  const parts: Record<string, PartLayout> = {
    core: part([0, 0, 0], [W, 27, core]),
    tank_0: part([0, 0, -core / 2 - tank / 2], [W + 2, 32, tank]),
    tank_1: part([0, 0, core / 2 + tank / 2], [W + 2, 32, tank]),
    fittings: part([W * 0.25, 18, -core / 2 - tank / 2], [W * 0.4, 10, 12]),
  };
  for (let i = 0; i < p.fanCount; i++) {
    parts[`fan_${i}`] = part([0, -13.5 - 12.5, -core / 2 + 2 + fan / 2 + i * fan], [fan, 25, fan]);
  }
  return {
    parts,
    mounts: {
      tube_in: { position: [W * 0.15, 22, -core / 2 - tank / 2] },
      tube_out: { position: [W * 0.35, 22, -core / 2 - tank / 2] },
    },
    bounds: { min: [-W / 2 - 1, -38.5, -L / 2], max: [W / 2 + 1, 23, L / 2] },
  };
}

export function pumpLayout(): Layout {
  return {
    parts: {
      block: part([22, 0, 0], [44, 68, 68]),
      cap: part([45, 0, 0], [3, 60, 60]),
      fittings: part([30, 0, 38], [14, 36, 12]),
    },
    mounts: {
      tube_in: { position: [30, -9, 45] },
      tube_out: { position: [30, 9, 45] },
    },
    bounds: { min: [0, -34, -34], max: [47, 34, 45] },
  };
}
