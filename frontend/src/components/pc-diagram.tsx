"use client";

import type { KeyboardEvent, ReactNode } from "react";
import type { BuildItem, Category, Finding, Status } from "@/lib/types";

/**
 * Schematic side view of the PC. Each part is clickable and linked to its explanation.
 * This is the always-available visual; a 3D renderer, if integrated later, is an enhancement on top of it.
 */
function statusOf(findings: Finding[], category: Category): Status | null {
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
      className={present ? "cursor-pointer outline-none transition-[filter] hover:brightness-95 [&:focus-visible>*]:stroke-[var(--accent)]" : ""}
      style={{ ["--part-stroke" as string]: stroke, ["--part-fill" as string]: fill }}
      strokeDasharray={present ? undefined : "4 4"}
      opacity={present ? 1 : 0.55}
    >
      <title>{present ? `${label}: ${item!.component.name}` : `${label} (não incluído)`}</title>
      {children}
    </g>
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

  const part = { fill: "var(--part-fill)", stroke: "var(--part-stroke)", strokeWidth: 2 } as const;
  const detail = { fill: "none", stroke: "var(--part-stroke)", strokeWidth: 1.5 } as const;

  return (
    <svg viewBox="0 0 320 420" className="h-auto w-full" role="group" aria-label="Diagrama do computador">
      {/* Case */}
      {renderPart("CASE", "Gabinete", <>
        <rect x="16" y="12" width="288" height="396" rx="18" {...part} />
        <rect x="30" y="26" width="260" height="368" rx="10" {...detail} strokeDasharray="2 6" />
        <circle cx="282" cy="46" r="5" {...detail} />
      </>)}

      {/* Motherboard */}
      {renderPart("MOTHERBOARD", "Placa-mãe", <>
        <rect x="56" y="44" width="196" height="236" rx="6" {...part} />
        <path d="M70 60h20M70 70h14M234 262h-24" {...detail} />
      </>)}

      {/* CPU (and stock cooler) */}
      {renderPart("CPU", "Processador", <>
        <rect x="102" y="78" width="60" height="60" rx="6" {...part} />
        {stockCooler ? (
          <>
            <circle cx="132" cy="108" r="24" {...detail} />
            <circle cx="132" cy="108" r="6" {...detail} />
          </>
        ) : (
          <rect x="116" y="92" width="32" height="32" rx="3" {...detail} />
        )}
      </>)}

      {/* Tower cooler */}
      {!stockCooler &&
        renderPart("CPU_COOLER", "Cooler do processador", <>
          <rect x="92" y="70" width="80" height="76" rx="8" {...part} fillOpacity={byCategory.has("CPU_COOLER") ? 0.92 : 0} />
          <circle cx="132" cy="108" r="26" {...detail} />
          <path d="M132 82v52M106 108h52M114 90l36 36M150 90l-36 36" {...detail} strokeWidth={1} />
        </>)}

      {/* Memory */}
      {renderPart("MEMORY", "Memória RAM", <>
        <rect x="190" y="64" width="12" height="96" rx="2" {...part} />
        <rect x="208" y="64" width="12" height="96" rx="2" {...part} />
      </>)}

      {/* Storage (M.2) */}
      {renderPart("STORAGE", "Armazenamento", <>
        <rect x="104" y="164" width="82" height="16" rx="3" {...part} />
        <path d="M112 172h10M128 172h10M144 172h10" {...detail} />
      </>)}

      {/* GPU */}
      {renderPart("GPU", "Placa de vídeo", <>
        <rect x="46" y="200" width="228" height="48" rx="8" {...part} />
        <circle cx="106" cy="224" r="17" {...detail} />
        <circle cx="160" cy="224" r="17" {...detail} />
        <circle cx="214" cy="224" r="17" {...detail} />
      </>)}

      {/* Power supply */}
      {renderPart("POWER_SUPPLY", "Fonte de alimentação", <>
        <rect x="46" y="316" width="138" height="66" rx="8" {...part} />
        <circle cx="115" cy="349" r="22" {...detail} />
        <path d="M100 349h30M115 334v30" {...detail} strokeWidth={1} />
      </>)}
    </svg>
  );
}
