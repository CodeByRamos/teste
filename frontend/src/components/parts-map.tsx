"use client";

import { useLayoutEffect, useRef, useState } from "react";
import { brl } from "@/lib/format";
import type { BuildItem, Category, Finding, Spec, Status } from "@/lib/types";
import { IsoPart, statusOf } from "./pc-diagram";
import { StatusIcon } from "./ui";

/**
 * Mind-map view of the build: the motherboard is the hub in the middle, the other parts line up three on each
 * side and the case sits below, every card the same size. Lines take the color of any compatibility problem between the two parts; the details of the
 * selected part open below the map.
 */

const NAMES: Record<Category, string> = {
  CASE: "Gabinete",
  MOTHERBOARD: "Placa-mãe",
  CPU: "Processador",
  CPU_COOLER: "Cooler",
  MEMORY: "Memória RAM",
  GPU: "Placa de vídeo",
  STORAGE: "Armazenamento",
  POWER_SUPPLY: "Fonte",
};

/** Map order (also the reading order on phones) and the part each one connects to. */
const NODES: { category: Category; parent: Category | null; area: string; indent: string }[] = [
  { category: "MOTHERBOARD", parent: null, area: "md:[grid-area:mb] md:self-center", indent: "" },
  { category: "CPU", parent: "MOTHERBOARD", area: "md:[grid-area:cpu]", indent: "ml-8 md:ml-0" },
  { category: "CPU_COOLER", parent: "CPU", area: "md:[grid-area:cooler]", indent: "ml-16 md:ml-0" },
  { category: "MEMORY", parent: "MOTHERBOARD", area: "md:[grid-area:memory]", indent: "ml-8 md:ml-0" },
  { category: "GPU", parent: "MOTHERBOARD", area: "md:[grid-area:gpu]", indent: "ml-8 md:ml-0" },
  { category: "STORAGE", parent: "MOTHERBOARD", area: "md:[grid-area:storage]", indent: "ml-8 md:ml-0" },
  { category: "POWER_SUPPLY", parent: "MOTHERBOARD", area: "md:[grid-area:psu]", indent: "ml-8 md:ml-0" },
  { category: "CASE", parent: "MOTHERBOARD", area: "md:[grid-area:case]", indent: "ml-8 md:ml-0" },
];

/** The two specs that best identify each kind of part. */
const KEY_SPECS: Record<Category, string[]> = {
  CASE: ["Formato", "Placa de vídeo até"],
  MOTHERBOARD: ["Chipset", "Tamanho"],
  CPU: ["Núcleos e threads", "Frequência máxima"],
  CPU_COOLER: ["Tipo", "Altura"],
  MEMORY: ["Capacidade", "Velocidade"],
  GPU: ["Memória de vídeo", "Consumo"],
  STORAGE: ["Capacidade", "Tipo"],
  POWER_SUPPLY: ["Potência", "Eficiência"],
};

const UNKNOWN = "Não informado";

function spec(item: BuildItem | undefined, label: string) {
  const value = item?.specs.find((s) => s.label === label)?.value;
  return value && value !== UNKNOWN ? value : null;
}

/** The known values of a part's two identifying specs. */
function keySpecs(item: BuildItem): Spec[] {
  return KEY_SPECS[item.category].flatMap((label) => {
    const value = spec(item, label);
    return value ? [{ label, value }] : [];
  });
}

/** Worst verified problem between a part and the one it connects to (or about the part alone). */
function edgeStatus(findings: Finding[], child: Category, parent: Category): Status | null {
  const relevant = findings.filter(
    (f) =>
      f.status !== "OK" &&
      f.ruleId !== "essential.missing" &&
      f.involves.includes(child) &&
      (f.involves.includes(parent) || f.involves.length === 1),
  );
  if (relevant.some((f) => f.status === "INCOMPATIBLE")) return "INCOMPATIBLE";
  if (relevant.some((f) => f.status === "WARNING" && f.verified)) return "WARNING";
  return null;
}

const STROKE: Record<Status | "none", string> = {
  none: "var(--border-strong)",
  OK: "var(--border-strong)",
  WARNING: "var(--warn)",
  INCOMPATIBLE: "var(--bad)",
};

type Edge = { key: string; d: string; status: Status | null; dashed: boolean };

/** Curved line between the parent and child boxes: sideways when they sit side by side, an elbow when stacked. */
function edgePath(p: DOMRect, c: DOMRect): string {
  const pcy = p.top + p.height / 2;
  const ccy = c.top + c.height / 2;
  if (c.right < p.left || c.left > p.right) {
    const toLeft = c.right < p.left;
    const x1 = toLeft ? p.left : p.right;
    const x2 = toLeft ? c.right : c.left;
    const mid = (x1 + x2) / 2;
    return `M${x1},${pcy} C${mid},${pcy} ${mid},${ccy} ${x2},${ccy}`;
  }
  if (c.bottom <= p.top) {
    const x = (Math.max(p.left, c.left) + Math.min(p.right, c.right)) / 2;
    return `M${x},${p.top} V${c.bottom}`;
  }
  if (c.left > p.left + 16) {
    const x = p.left + Math.min(16, (c.left - p.left) / 2);
    const r = Math.min(8, (ccy - p.bottom) / 2);
    return `M${x},${p.bottom} V${ccy - r} Q${x},${ccy} ${x + r},${ccy} H${c.left}`;
  }
  const x = (Math.max(p.left, c.left) + Math.min(p.right, c.right)) / 2;
  return `M${x},${p.bottom} V${c.top}`;
}

export function PartsMap({
  items,
  findings,
  selected,
  onSelect,
}: {
  items: BuildItem[];
  findings: Finding[];
  selected: Category | null;
  onSelect: (category: Category) => void;
}) {
  const byCategory = new Map(items.map((item) => [item.category, item]));
  const listRef = useRef<HTMLUListElement>(null);
  const [edges, setEdges] = useState<Edge[]>([]);
  const cpuHasCooler = spec(byCategory.get("CPU"), "Cooler na caixa") === "Sim";
  const itemsKey = items.map((item) => item.component.id).join();
  const findingsKey = findings.map((f) => f.ruleId + f.status).join();

  useLayoutEffect(() => {
    const list = listRef.current;
    if (!list) return;
    const measure = () => {
      const origin = list.getBoundingClientRect();
      const rect = (category: Category) => {
        const r = list.querySelector(`[data-node="${category}"]`)?.getBoundingClientRect();
        return r && new DOMRect(r.left - origin.left, r.top - origin.top, r.width, r.height);
      };
      const next: Edge[] = [];
      for (const node of NODES) {
        if (!node.parent) continue;
        const p = rect(node.parent);
        const c = rect(node.category);
        if (!p || !c) continue;
        next.push({
          key: node.category,
          d: edgePath(p, c),
          status: edgeStatus(findings, node.category, node.parent),
          dashed: !byCategory.has(node.category) || !byCategory.has(node.parent),
        });
      }
      // Problems are drawn last so shared stretches of line show them.
      next.sort((a, b) => Number(Boolean(a.status)) - Number(Boolean(b.status)));
      setEdges(next);
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(list);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- keyed on content, not on array identity
  }, [itemsKey, findingsKey]);

  return (
    <ul
      ref={listRef}
      className="relative grid gap-3 rounded-2xl border-2 border-dashed border-border-strong bg-surface p-3 sm:p-5 md:auto-rows-fr md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)] md:gap-x-12 md:gap-y-5 md:[grid-template-areas:'cpu_mb_gpu'_'cooler_mb_storage'_'memory_mb_psu'_'._case_.']"
      aria-label="Mapa das peças"
    >
      <svg className="pointer-events-none absolute inset-0 size-full overflow-visible" aria-hidden>
        {edges.map((edge) => (
          <path
            key={edge.key}
            d={edge.d}
            fill="none"
            stroke={STROKE[edge.status ?? "none"]}
            strokeWidth={edge.status ? 2.5 : 1.75}
            strokeDasharray={edge.dashed ? "4 4" : undefined}
            strokeLinecap="round"
          />
        ))}
      </svg>
      {NODES.map((node) => (
        <li key={node.category} className={`relative min-w-0 ${node.area} ${node.indent} ${node.parent ? "md:h-full" : ""}`}>
          <PartNode
            category={node.category}
            item={byCategory.get(node.category)}
            status={statusOf(findings, node.category)}
            emptyText={node.category === "CPU_COOLER" && cpuHasCooler ? "Usa o que vem com o processador" : "Não incluído"}
            selected={selected === node.category}
            onSelect={() => onSelect(node.category)}
          />
        </li>
      ))}
    </ul>
  );
}

function PartNode({
  category,
  item,
  status,
  emptyText,
  selected,
  onSelect,
}: {
  category: Category;
  item: BuildItem | undefined;
  status: Status | null;
  emptyText: string;
  selected: boolean;
  onSelect: () => void;
}) {
  const name = item?.categoryLabel ?? NAMES[category];
  const iconTile = (
    <span className="grid size-12 shrink-0 place-items-center">
      <IsoPart category={category} active={selected} className="size-12" />
    </span>
  );
  if (!item) {
    return (
      <div
        data-node={category}
        className="flex h-full items-center gap-3 rounded-xl border border-dashed border-border-strong bg-surface/60 p-3 text-sm text-muted"
      >
        <IsoPart category={category} className="size-12 shrink-0 opacity-50" />
        <span>
          <span className="block text-xs font-medium tracking-wide uppercase">{name}</span>
          {emptyText}
        </span>
      </div>
    );
  }
  const chips = keySpecs(item).map((s) => s.value);
  const border =
    status === "INCOMPATIBLE" ? "border-bad" : status === "WARNING" ? "border-warn" : selected ? "border-accent" : "border-border";
  return (
    <button
      type="button"
      data-node={category}
      onClick={onSelect}
      aria-pressed={selected}
      aria-controls={selected ? "detalhes-peca" : undefined}
      className={`group flex h-full w-full flex-col rounded-xl border-2 bg-surface p-3 text-left shadow-sm transition-[border-color,box-shadow,translate] duration-200 ease-out hover:-translate-y-0.5 hover:border-accent hover:shadow-[0_10px_28px_-10px_rgb(147_51_234/0.55)] focus-visible:-translate-y-0.5 ${border}`}
    >
      <span className="flex items-start gap-3">
        {iconTile}
        <span className="min-w-0 flex-1">
          <span className="flex items-start justify-between gap-2">
            <span className="text-xs font-semibold tracking-wide text-muted uppercase">{name}</span>
            {status && <StatusIcon status={status} className="size-3" />}
          </span>
          <span className="mt-0.5 line-clamp-2 h-10 overflow-hidden text-sm font-semibold text-pretty">{item.component.name}</span>
        </span>
      </span>
      {chips.length > 0 && (
        <span className="mt-2 mb-2 flex gap-1 overflow-hidden">
          {chips.map((chip) => (
            <span key={chip} className="min-w-0 truncate rounded-md bg-accent-soft px-1.5 py-0.5 text-xs text-accent">
              {chip}
            </span>
          ))}
        </span>
      )}
      <span className="mt-auto flex items-center justify-end gap-2 border-t border-border pt-1.5 text-xs">
        {item.owned ? (
          <span className="font-semibold text-ok">Você já tem</span>
        ) : item.price ? (
          <span className="font-semibold whitespace-nowrap tabular-nums">{brl(item.price.amountBrl)}</span>
        ) : (
          <span className="text-subtle">Sem preço</span>
        )}
      </span>
    </button>
  );
}
