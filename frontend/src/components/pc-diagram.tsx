"use client";

import type { KeyboardEvent, ReactNode } from "react";
import type { BuildItem, Category, Finding, Status } from "@/lib/types";

/**
 * Exploded view of the PC: every part drawn apart in isometric line art, each with a callout label tied to it
 * by a leader line. Each part (and its label) is clickable and linked to its explanation. The same drawings
 * serve as the parts' icons elsewhere (IsoPart).
 * This is the always-available visual; a 3D renderer, if integrated later, is an enhancement on top of it.
 */
export function statusOf(findings: Finding[], category: Category): Status | null {
  // Parts that are simply not chosen yet are drawn dashed; only real problems get a status color.
  const relevant = findings.filter(
    (finding) => finding.involves.includes(category) && finding.status !== "OK" && finding.ruleId !== "essential.missing",
  );
  if (relevant.some((finding) => finding.status === "INCOMPATIBLE")) return "INCOMPATIBLE";
  if (relevant.some((finding) => finding.status === "WARNING" && finding.verified)) return "WARNING";
  return null;
}

function DiagramPart({
  label,
  item,
  status,
  selected,
  hovered,
  onSelect,
  children,
}: {
  label: string;
  item: BuildItem | undefined;
  status: Status | null;
  selected: boolean;
  hovered: boolean;
  onSelect: () => void;
  children: ReactNode;
}) {
  const present = Boolean(item);
  const stroke =
    status === "INCOMPATIBLE" ? "var(--bad)" : status === "WARNING" ? "var(--warn)" : "#61596e";
  const fill = present ? "var(--surface)" : "transparent";
  const onKey = (event: KeyboardEvent) => {
    if (present && (event.key === "Enter" || event.key === " ")) {
      event.preventDefault();
      onSelect();
    }
  };
  return (
    <g
      role={present ? "button" : undefined}
      tabIndex={present ? 0 : -1}
      aria-label={present ? `${label}: ${item!.component.name}` : `${label}: não incluído`}
      aria-pressed={present ? selected : undefined}
      onClick={() => present && onSelect()}
      onKeyDown={onKey}
      className="diagram-part outline-none"
      data-interactive={present}
      data-selected={selected}
      data-hovered={hovered}
      data-status={status ?? undefined}
      style={{ ["--base-stroke" as string]: stroke, ["--base-fill" as string]: fill }}
      strokeDasharray={present ? undefined : "4 4"}
      opacity={present ? 1 : 0.55}
    >
      <title>{present ? `${label}: ${item!.component.name}` : `${label} (não incluído)`}</title>
      {children}
    </g>
  );
}

type Point3 = [number, number, number];
const COS30 = Math.cos(Math.PI / 6);

/** Isometric projection anchored at a screen point: x runs down-right, y down-left, z up. */
function projector([ox, oy]: [number, number]) {
  return ([x, y, z]: Point3): [number, number] => [ox + (x - y) * COS30, oy + (x + y) * 0.5 - z];
}

type Project = ReturnType<typeof projector>;

function polygon(project: Project, points: Point3[], closed = true) {
  const path = points.map((point, index) => `${index ? "L" : "M"}${project(point).map((v) => v.toFixed(1)).join(" ")}`).join("");
  return closed ? `${path}Z` : path;
}

/** A circle lying on one of a box's visible planes, projected (an ellipse on screen). */
function circle(project: Project, plane: "top" | "front" | "side", center: Point3, r: number) {
  const points: Point3[] = [];
  for (let i = 0; i < 28; i++) {
    const angle = (i / 28) * Math.PI * 2;
    const [c, s] = [Math.cos(angle) * r, Math.sin(angle) * r];
    const [x, y, z] = center;
    points.push(plane === "top" ? [x + c, y + s, z] : plane === "front" ? [x + c, y, z + s] : [x, y + c, z + s]);
  }
  return polygon(project, points);
}

const FACE = { fill: "var(--part-fill)", stroke: "var(--part-stroke)", strokeWidth: 0.8, strokeLinejoin: "miter" } as const;
const DETAIL = { fill: "none", stroke: "var(--part-stroke)", strokeWidth: 0.8, strokeLinejoin: "miter" } as const;

/**
 * The three visible faces of a w × d × h box, lit from the top left: a bright top, a front in half light and a
 * side in shade, a rim light on the top edges facing the viewer, and (for parts resting on the ground) a soft
 * contact shadow underneath.
 */
function Box({
  project,
  w,
  d,
  h,
  at = [0, 0, 0],
  grounded = false,
}: {
  project: Project;
  w: number;
  d: number;
  h: number;
  at?: Point3;
  grounded?: boolean;
}) {
  const [x, y, z] = at;
  const faces: { points: Point3[]; shade: string }[] = [
    { points: [[x, y, z + h], [x + w, y, z + h], [x + w, y + d, z + h], [x, y + d, z + h]], shade: "url(#pcd-top)" },
    { points: [[x, y + d, z], [x + w, y + d, z], [x + w, y + d, z + h], [x, y + d, z + h]], shade: "url(#pcd-front)" },
    { points: [[x + w, y, z], [x + w, y + d, z], [x + w, y + d, z + h], [x + w, y, z + h]], shade: "url(#pcd-side)" },
  ];
  const spread = Math.max(3, Math.min(w, d) * 0.12);
  return (
    <>
      {grounded && (
        <path
          d={polygon(project, [[x - spread * 0.4, y + spread * 0.2, z], [x + w + spread, y + spread * 0.2, z], [x + w + spread, y + d + spread, z], [x - spread * 0.4, y + d + spread, z]])}
          fill="black"
          fillOpacity={0.45}
          filter="url(#pcd-soft)"
          stroke="none"
        />
      )}
      {faces.map(({ points, shade }, index) => (
        <g key={index}>
          <path d={polygon(project, points)} {...FACE} />
          <path d={polygon(project, points)} fill={shade} stroke="none" />
        </g>
      ))}
      <path
        d={polygon(project, [[x, y + d, z + h], [x + w, y + d, z + h], [x + w, y, z + h]], false)}
        fill="none"
        stroke="white"
        strokeOpacity={0.28}
        strokeWidth={0.8}
        strokeLinecap="round"
      />
    </>
  );
}

/** Callout: rounded label, leader line with an elbow, and a ring marking the spot on the part. */
function Callout({ label, at, to }: { label: string; at: [number, number]; to: [number, number] }) {
  const width = label.length * 5.6 + 16;
  const [lx, ly] = at;
  const [tx, ty] = to;
  const below = ty > ly;
  const fromY = below ? ly + 10 : ly - 10;
  const endY = below ? ty - 5 : ty + 5;
  const midY = (fromY + endY) / 2;
  return (
    <>
      <path d={`M${lx} ${fromY}V${midY}H${tx}V${endY}`} fill="none" stroke="var(--part-line)" strokeWidth={0.9} />
      <circle cx={tx} cy={ty} r={3.8} fill="var(--surface)" stroke="var(--part-line)" strokeWidth={1} />
      <circle cx={tx} cy={ty} r={1.4} fill="var(--accent)" stroke="none" />
      <rect x={lx - width / 2} y={ly - 10} width={width} height={20} rx={2} fill="var(--label-fill)" stroke="var(--part-line)" strokeWidth={0.9} />
      <text x={lx} y={ly + 3.6} textAnchor="middle" fontSize={10} fontWeight={500} fill="var(--label-text)" stroke="none">
        {label}
      </text>
    </>
  );
}

// Where each part sits in the diagram (three columns centered at 55, 160 and 265).
const ram = projector([33, 83]);
const cpu = projector([160, 80]);
const pcCase = projector([253, 172]);
const cooler = projector([55, 207]);
const gpu = projector([122, 196]);
const storage = projector([48, 336]);
const psu = projector([155, 346]);
const board = projector([262, 322]);

function IsoDefs() {
  return (
    <defs>
      <linearGradient id="pcd-top" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stopColor="white" stopOpacity={0.2} />
        <stop offset="1" stopColor="white" stopOpacity={0.07} />
      </linearGradient>
      <linearGradient id="pcd-front" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="white" stopOpacity={0.04} />
        <stop offset="1" stopColor="black" stopOpacity={0.2} />
      </linearGradient>
      <linearGradient id="pcd-side" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" stopColor="black" stopOpacity={0.34} />
        <stop offset="1" stopColor="black" stopOpacity={0.52} />
      </linearGradient>
      <radialGradient id="pcd-well" cx="0.4" cy="0.35" r="0.75">
        <stop offset="0" stopColor="black" stopOpacity={0.05} />
        <stop offset="1" stopColor="black" stopOpacity={0.4} />
      </radialGradient>
      <linearGradient id="pcd-glass" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stopColor="white" stopOpacity={0.12} />
        <stop offset="0.45" stopColor="white" stopOpacity={0.02} />
        <stop offset="0.5" stopColor="white" stopOpacity={0.08} />
        <stop offset="1" stopColor="white" stopOpacity={0} />
      </linearGradient>
      <filter id="pcd-soft" x="-30%" y="-30%" width="160%" height="160%">
        <feGaussianBlur stdDeviation="3" />
      </filter>
    </defs>
  );
}

/** Each part's drawing, in diagram coordinates. */
const DRAWINGS: Record<Category, ReactNode> = {
  CASE: (
    <>
        <Box project={pcCase} w={64} d={36} h={112} grounded />
        {/* Glass side with a fan behind it; front panel with power button and vents. */}
        <path d={polygon(pcCase, [[8, 36, 10], [56, 36, 10], [56, 36, 102], [8, 36, 102]])} fill="url(#pcd-glass)" stroke="var(--part-stroke)" strokeWidth={0.8} />
        <path d={circle(pcCase, "front", [32, 36, 70], 15)} fill="url(#pcd-well)" stroke="var(--part-stroke)" strokeWidth={0.8} />
        <path d={circle(pcCase, "front", [32, 36, 70], 5)} {...DETAIL} />
        <path d={circle(pcCase, "side", [64, 18, 98], 3.5)} {...DETAIL} />
        {[70, 62, 54, 46, 38].map((z) => (
          <path key={z} d={polygon(pcCase, [[64, 8, z], [64, 28, z]], false)} {...DETAIL} />
        ))}
    </>
  ),
  MEMORY: (
    <>
        {[0, 11].map((y) => (
          <g key={y}>
            <Box project={ram} w={64} d={3} h={15} at={[0, y, 0]} grounded={y === 0} />
            {[6, 21, 36, 51].map((x) => (
              <path key={x} d={polygon(ram, [[x, y + 3, 4], [x + 9, y + 3, 4], [x + 9, y + 3, 11], [x, y + 3, 11]])} {...DETAIL} />
            ))}
          </g>
        ))}
    </>
  ),
  CPU: (
    <>
        <Box project={cpu} w={40} d={40} h={5} grounded />
        <path d={polygon(cpu, [[9, 9, 5], [31, 9, 5], [31, 31, 5], [9, 31, 5]])} {...DETAIL} />
        <path d={polygon(cpu, [[3, 3, 5], [8, 3, 5], [3, 8, 5]])} fill="var(--part-stroke)" stroke="none" />
    </>
  ),
  CPU_COOLER: (
    <>
        <Box project={cooler} w={36} d={36} h={40} grounded />
        {/* Fin stack on the side, fan on the front. */}
        {[6, 11, 16, 21, 26, 31, 36].map((z) => (
          <path key={z} d={polygon(cooler, [[36, 4, z], [36, 32, z]], false)} {...DETAIL} />
        ))}
        <path d={circle(cooler, "front", [18, 36, 20], 14)} fill="url(#pcd-well)" stroke="var(--part-stroke)" strokeWidth={0.8} />
        <path d={circle(cooler, "front", [18, 36, 20], 4)} {...DETAIL} />
    </>
  ),
  GPU: (
    <>
        <Box project={gpu} w={92} d={8} h={26} grounded />
        {/* Bracket, two fans on the shroud, gold contacts under the card. */}
        <path d={polygon(gpu, [[-3, 8, -6], [0, 8, -6], [0, 8, 32], [-3, 8, 32]])} {...DETAIL} />
        <path d={circle(gpu, "front", [30, 8, 13], 10)} fill="url(#pcd-well)" stroke="var(--part-stroke)" strokeWidth={0.8} />
        <path d={circle(gpu, "front", [30, 8, 13], 3)} {...DETAIL} />
        <path d={circle(gpu, "front", [64, 8, 13], 10)} fill="url(#pcd-well)" stroke="var(--part-stroke)" strokeWidth={0.8} />
        <path d={circle(gpu, "front", [64, 8, 13], 3)} {...DETAIL} />
        <path d={polygon(gpu, [[14, 8, -4], [52, 8, -4], [52, 8, 0], [14, 8, 0]])} {...DETAIL} />
    </>
  ),
  STORAGE: (
    <>
        <Box project={storage} w={46} d={30} h={6} grounded />
        <path d={polygon(storage, [[8, 6, 6], [38, 6, 6], [38, 24, 6], [8, 24, 6]])} {...DETAIL} />
        <path d={polygon(storage, [[13, 12, 6], [33, 12, 6]], false)} {...DETAIL} />
        <path d={polygon(storage, [[13, 17, 6], [26, 17, 6]], false)} {...DETAIL} />
    </>
  ),
  POWER_SUPPLY: (
    <>
        <Box project={psu} w={52} d={40} h={36} grounded />
        {/* Fan grille on top, power socket and switch on the side. */}
        <path d={circle(psu, "top", [26, 20, 36], 15)} fill="url(#pcd-well)" stroke="var(--part-stroke)" strokeWidth={0.8} />
        <path d={circle(psu, "top", [26, 20, 36], 9)} {...DETAIL} />
        <path d={circle(psu, "top", [26, 20, 36], 3)} {...DETAIL} />
        <path d={polygon(psu, [[52, 8, 10], [52, 20, 10], [52, 20, 20], [52, 8, 20]])} {...DETAIL} />
        <path d={polygon(psu, [[52, 26, 12], [52, 32, 12], [52, 32, 18], [52, 26, 18]])} {...DETAIL} />
    </>
  ),
  MOTHERBOARD: (
    <>
        <Box project={board} w={58} d={52} h={4} grounded />
        {/* Socket, memory slots, expansion slot and chipset on the top face. */}
        <path d={polygon(board, [[10, 10, 4], [24, 10, 4], [24, 24, 4], [10, 24, 4]])} {...DETAIL} />
        <path d={polygon(board, [[30, 8, 4], [33, 8, 4], [33, 30, 4], [30, 30, 4]])} {...DETAIL} />
        <path d={polygon(board, [[36, 8, 4], [39, 8, 4], [39, 30, 4], [36, 30, 4]])} {...DETAIL} />
        <path d={polygon(board, [[8, 34, 4], [48, 34, 4], [48, 37, 4], [8, 37, 4]])} {...DETAIL} />
        <path d={polygon(board, [[40, 40, 4], [48, 40, 4], [48, 48, 4], [40, 48, 4]])} {...DETAIL} />
    </>
  ),
};

/** The part's area in diagram coordinates, as a square, so a drawing can be shown on its own as an icon. */
const VIEWBOX: Record<Category, string> = {
  CASE: "180 56 170 170",
  MEMORY: "16.6 57 76 76",
  CPU: "121 58.5 78 78",
  CPU_COOLER: "13 163 84 84",
  GPU: "106 157 100 100",
  STORAGE: "18 315 74 74",
  POWER_SUPPLY: "115 306 90 90",
  MOTHERBOARD: "213.5 296.5 102 102",
};

/**
 * A part's isometric drawing on its own, used as its icon (parts map, details, sheet). Violet line work at rest;
 * filled solid violet when active or when an enclosing `.group` is hovered.
 */
export function IsoPart({ category, active = false, className = "" }: { category: Category; active?: boolean; className?: string }) {
  return (
    <svg viewBox={VIEWBOX[category]} className={`iso-part overflow-visible ${className}`} data-active={active} aria-hidden>
      <IsoDefs />
      {DRAWINGS[category]}
    </svg>
  );
}

/** Callout positions: label center and the spot it points at. */
const CALLOUTS: Record<Category, { at: [number, number]; to: [number, number] }> = {
  CASE: { at: [265, 18], to: pcCase([24, 0, 112]) },
  MEMORY: { at: [55, 18], to: ram([30, 0, 15]) },
  CPU: { at: [160, 18], to: cpu([20, 20, 5]) },
  CPU_COOLER: { at: [55, 266], to: cooler([18, 36, 4]) },
  GPU: { at: [160, 266], to: gpu([47, 8, 2]) },
  STORAGE: { at: [55, 424], to: storage([23, 30, 0]) },
  POWER_SUPPLY: { at: [160, 424], to: psu([26, 40, 0]) },
  MOTHERBOARD: { at: [265, 424], to: board([29, 52, 0]) },
};

const LABELS: Record<Category, string> = {
  CASE: "Gabinete",
  MEMORY: "Memória RAM",
  CPU: "Processador",
  CPU_COOLER: "Cooler",
  GPU: "Placa de vídeo",
  STORAGE: "Armazenamento",
  POWER_SUPPLY: "Fonte",
  MOTHERBOARD: "Placa-mãe",
};

export function PcDiagram({
  items,
  findings,
  selected,
  hovered = null,
  onSelect,
  stockCooler,
}: {
  items: BuildItem[];
  findings: Finding[];
  selected: Category | null;
  /** Part hovered in another view (map or sheet), shown here as if hovered. */
  hovered?: Category | null;
  onSelect: (category: Category) => void;
  stockCooler: boolean;
}) {
  const byCategory = new Map(items.map((item) => [item.category, item]));
  const labelOf = (category: Category) => (category === "CPU_COOLER" && stockCooler ? "Cooler (na caixa)" : LABELS[category]);

  return (
    <svg viewBox="0 0 320 440" className="h-auto w-full overflow-visible" role="group" aria-label="Diagrama do computador">
      <IsoDefs />
      {(Object.keys(DRAWINGS) as Category[]).map((category) => (
        <DiagramPart
          key={category}
          label={category === "POWER_SUPPLY" ? "Fonte de alimentação" : labelOf(category)}
          item={byCategory.get(category)}
          status={statusOf(findings, category)}
          selected={selected === category}
          hovered={hovered === category}
          onSelect={() => onSelect(category)}
        >
          {DRAWINGS[category]}
          <Callout label={labelOf(category)} at={CALLOUTS[category].at} to={CALLOUTS[category].to} />
        </DiagramPart>
      ))}
    </svg>
  );
}
