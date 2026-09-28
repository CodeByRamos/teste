"use client";

import type { KeyboardEvent, ReactNode } from "react";
import type { BuildItem, Category, Finding, Status } from "@/lib/types";

/**
 * Exploded view of the PC: every part drawn apart in isometric line art, each with a callout label tied to it
 * by a leader line. Each part (and its label) is clickable and linked to its explanation.
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
  onSelect,
  children,
}: {
  label: string;
  item: BuildItem | undefined;
  status: Status | null;
  selected: boolean;
  onSelect: () => void;
  children: ReactNode;
}) {
  const present = Boolean(item);
  const stroke =
    status === "INCOMPATIBLE" ? "var(--bad)" : status === "WARNING" ? "var(--warn)" : selected ? "var(--accent)" : "var(--border-strong)";
  const fill = selected ? "var(--accent-soft)" : present ? "var(--surface)" : "transparent";
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
      className={present ? "cursor-pointer outline-none transition-[filter] hover:brightness-125 focus-visible:brightness-125" : ""}
      style={{ ["--part-stroke" as string]: stroke, ["--part-fill" as string]: fill }}
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

const FACE = { fill: "var(--part-fill)", stroke: "var(--part-stroke)", strokeWidth: 1.5, strokeLinejoin: "round" } as const;
const DETAIL = { fill: "none", stroke: "var(--part-stroke)", strokeWidth: 1.1, strokeLinejoin: "round" } as const;

/** The three visible faces of a w × d × h box: top (lit), front (the y = d face) and side (the x = w face, shaded). */
function Box({ project, w, d, h, at = [0, 0, 0] }: { project: Project; w: number; d: number; h: number; at?: Point3 }) {
  const [x, y, z] = at;
  const faces: { points: Point3[]; shade: string; opacity: number }[] = [
    { points: [[x, y, z + h], [x + w, y, z + h], [x + w, y + d, z + h], [x, y + d, z + h]], shade: "white", opacity: 0.07 },
    { points: [[x, y + d, z], [x + w, y + d, z], [x + w, y + d, z + h], [x, y + d, z + h]], shade: "black", opacity: 0 },
    { points: [[x + w, y, z], [x + w, y + d, z], [x + w, y + d, z + h], [x + w, y, z + h]], shade: "black", opacity: 0.22 },
  ];
  return (
    <>
      {faces.map(({ points, shade, opacity }, index) => (
        <g key={index}>
          <path d={polygon(project, points)} {...FACE} />
          {opacity > 0 && <path d={polygon(project, points)} fill={shade} fillOpacity={opacity} stroke="none" />}
        </g>
      ))}
    </>
  );
}

/** Callout: rounded label, leader line with an elbow, and a ring marking the spot on the part. */
function Callout({ label, at, to }: { label: string; at: [number, number]; to: [number, number] }) {
  const width = label.length * 6.1 + 18;
  const [lx, ly] = at;
  const [tx, ty] = to;
  const below = ty > ly;
  const fromY = below ? ly + 10 : ly - 10;
  const endY = below ? ty - 5 : ty + 5;
  const midY = (fromY + endY) / 2;
  return (
    <>
      <path d={`M${lx} ${fromY}V${midY}H${tx}V${endY}`} fill="none" stroke="var(--part-stroke)" strokeWidth={1.25} />
      <circle cx={tx} cy={ty} r={5} fill="var(--surface)" stroke="var(--part-stroke)" strokeWidth={1.5} />
      <circle cx={tx} cy={ty} r={1.8} fill="var(--part-stroke)" stroke="none" />
      <rect x={lx - width / 2} y={ly - 10} width={width} height={20} rx={6} fill="var(--surface)" stroke="var(--part-stroke)" strokeWidth={1.25} />
      <text x={lx} y={ly + 3.6} textAnchor="middle" fontSize={10.5} fontWeight={600} fill="var(--foreground)" stroke="none">
        {label}
      </text>
    </>
  );
}

export function PcDiagram({
  items,
  findings,
  selected,
  onSelect,
  stockCooler,
}: {
  items: BuildItem[];
  findings: Finding[];
  selected: Category | null;
  onSelect: (category: Category) => void;
  stockCooler: boolean;
}) {
  const byCategory = new Map(items.map((item) => [item.category, item]));
  const renderPart = (category: Category, label: string, children: ReactNode) => (
    <DiagramPart
      label={label}
      item={byCategory.get(category)}
      status={statusOf(findings, category)}
      selected={selected === category}
      onSelect={() => onSelect(category)}
    >
      {children}
    </DiagramPart>
  );
  const coolerLabel = stockCooler ? "Cooler (na caixa)" : "Cooler";

  // Three columns (centers 55, 160, 265) and three rows of parts; labels above the first row, under the others.
  const ram = projector([33, 83]);
  const cpu = projector([160, 80]);
  const pcCase = projector([253, 172]);
  const cooler = projector([55, 207]);
  const gpu = projector([122, 196]);
  const storage = projector([48, 336]);
  const psu = projector([155, 346]);
  const board = projector([262, 322]);

  return (
    <svg viewBox="0 0 320 440" className="h-auto w-full" role="group" aria-label="Diagrama do computador">
      {renderPart("CASE", "Gabinete", <>
        <Box project={pcCase} w={64} d={36} h={112} />
        {/* Glass side with a fan behind it; front panel with power button and vents. */}
        <path d={polygon(pcCase, [[8, 36, 10], [56, 36, 10], [56, 36, 102], [8, 36, 102]])} {...DETAIL} />
        <path d={circle(pcCase, "front", [32, 36, 70], 15)} {...DETAIL} />
        <path d={circle(pcCase, "front", [32, 36, 70], 5)} {...DETAIL} />
        <path d={circle(pcCase, "side", [64, 18, 98], 3.5)} {...DETAIL} />
        {[70, 62, 54, 46, 38].map((z) => (
          <path key={z} d={polygon(pcCase, [[64, 8, z], [64, 28, z]], false)} {...DETAIL} />
        ))}
        <Callout label="Gabinete" at={[265, 18]} to={pcCase([24, 0, 112])} />
      </>)}

      {renderPart("MEMORY", "Memória RAM", <>
        {[0, 11].map((y) => (
          <g key={y}>
            <Box project={ram} w={64} d={3} h={15} at={[0, y, 0]} />
            {[6, 21, 36, 51].map((x) => (
              <path key={x} d={polygon(ram, [[x, y + 3, 4], [x + 9, y + 3, 4], [x + 9, y + 3, 11], [x, y + 3, 11]])} {...DETAIL} />
            ))}
          </g>
        ))}
        <Callout label="Memória RAM" at={[55, 18]} to={ram([30, 0, 15])} />
      </>)}

      {renderPart("CPU", "Processador", <>
        <Box project={cpu} w={40} d={40} h={5} />
        <path d={polygon(cpu, [[9, 9, 5], [31, 9, 5], [31, 31, 5], [9, 31, 5]])} {...DETAIL} />
        <path d={polygon(cpu, [[3, 3, 5], [8, 3, 5], [3, 8, 5]])} fill="var(--part-stroke)" stroke="none" />
        <Callout label="Processador" at={[160, 18]} to={cpu([20, 20, 5])} />
      </>)}

      {renderPart("CPU_COOLER", coolerLabel, <>
        <Box project={cooler} w={36} d={36} h={40} />
        {/* Fin stack on the side, fan on the front. */}
        {[6, 11, 16, 21, 26, 31, 36].map((z) => (
          <path key={z} d={polygon(cooler, [[36, 4, z], [36, 32, z]], false)} {...DETAIL} />
        ))}
        <path d={circle(cooler, "front", [18, 36, 20], 14)} {...DETAIL} />
        <path d={circle(cooler, "front", [18, 36, 20], 4)} {...DETAIL} />
        <Callout label={coolerLabel} at={[55, 266]} to={cooler([18, 36, 4])} />
      </>)}

      {renderPart("GPU", "Placa de vídeo", <>
        <Box project={gpu} w={92} d={8} h={26} />
        {/* Bracket, two fans on the shroud, gold contacts under the card. */}
        <path d={polygon(gpu, [[-3, 8, -6], [0, 8, -6], [0, 8, 32], [-3, 8, 32]])} {...DETAIL} />
        <path d={circle(gpu, "front", [30, 8, 13], 10)} {...DETAIL} />
        <path d={circle(gpu, "front", [30, 8, 13], 3)} {...DETAIL} />
        <path d={circle(gpu, "front", [64, 8, 13], 10)} {...DETAIL} />
        <path d={circle(gpu, "front", [64, 8, 13], 3)} {...DETAIL} />
        <path d={polygon(gpu, [[14, 8, -4], [52, 8, -4], [52, 8, 0], [14, 8, 0]])} {...DETAIL} />
        <Callout label="Placa de vídeo" at={[160, 266]} to={gpu([47, 8, 2])} />
      </>)}

      {renderPart("STORAGE", "Armazenamento", <>
        <Box project={storage} w={46} d={30} h={6} />
        <path d={polygon(storage, [[8, 6, 6], [38, 6, 6], [38, 24, 6], [8, 24, 6]])} {...DETAIL} />
        <path d={polygon(storage, [[13, 12, 6], [33, 12, 6]], false)} {...DETAIL} />
        <path d={polygon(storage, [[13, 17, 6], [26, 17, 6]], false)} {...DETAIL} />
        <Callout label="Armazenamento" at={[55, 424]} to={storage([23, 30, 0])} />
      </>)}

      {renderPart("POWER_SUPPLY", "Fonte de alimentação", <>
        <Box project={psu} w={52} d={40} h={36} />
        {/* Fan grille on top, power socket and switch on the side. */}
        <path d={circle(psu, "top", [26, 20, 36], 15)} {...DETAIL} />
        <path d={circle(psu, "top", [26, 20, 36], 9)} {...DETAIL} />
        <path d={circle(psu, "top", [26, 20, 36], 3)} {...DETAIL} />
        <path d={polygon(psu, [[52, 8, 10], [52, 20, 10], [52, 20, 20], [52, 8, 20]])} {...DETAIL} />
        <path d={polygon(psu, [[52, 26, 12], [52, 32, 12], [52, 32, 18], [52, 26, 18]])} {...DETAIL} />
        <Callout label="Fonte" at={[160, 424]} to={psu([26, 40, 0])} />
      </>)}

      {renderPart("MOTHERBOARD", "Placa-mãe", <>
        <Box project={board} w={58} d={52} h={4} />
        {/* Socket, memory slots, expansion slot and chipset on the top face. */}
        <path d={polygon(board, [[10, 10, 4], [24, 10, 4], [24, 24, 4], [10, 24, 4]])} {...DETAIL} />
        <path d={polygon(board, [[30, 8, 4], [33, 8, 4], [33, 30, 4], [30, 30, 4]])} {...DETAIL} />
        <path d={polygon(board, [[36, 8, 4], [39, 8, 4], [39, 30, 4], [36, 30, 4]])} {...DETAIL} />
        <path d={polygon(board, [[8, 34, 4], [48, 34, 4], [48, 37, 4], [8, 37, 4]])} {...DETAIL} />
        <path d={polygon(board, [[40, 40, 4], [48, 40, 4], [48, 48, 4], [40, 48, 4]])} {...DETAIL} />
        <Callout label="Placa-mãe" at={[265, 424]} to={board([29, 52, 0])} />
      </>)}
    </svg>
  );
}
