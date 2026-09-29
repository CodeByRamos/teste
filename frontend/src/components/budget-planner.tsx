"use client";

import type { BuildItem, BuildView, Category } from "@/lib/types";
import { IsoPart } from "./pc-diagram";
import { Price } from "./ui";

/** Violet shades for the parts, from the most to the least expensive. */
const SHADES = ["#7e22ce", "#9333ea", "#a855f7", "#c084fc", "#6d28d9", "#d8b4fe", "#5b21b6", "#e9d5ff"];

function cost(item: BuildItem) {
  return item.owned ? 0 : (item.price?.amountBrl ?? 0);
}

/**
 * Spending planner: how the budget is split across the parts. Budget, spent and what is left on top; a stacked
 * bar of every part's share; then the parts from the most to the least expensive, each with its share of the total.
 * Clicking a row selects the part; hovering the map or the sheet highlights its row. Hovering here stays local.
 */
export function BudgetPlanner({
  items,
  totals,
  selected,
  hovered,
  onSelect,
}: {
  items: BuildItem[];
  totals: BuildView["totals"];
  selected: Category | null;
  hovered: Category | null;
  onSelect: (category: Category) => void;
}) {
  const rows = [...items].sort((a, b) => cost(b) - cost(a));
  const spent = totals.totalBrl;
  const budget = totals.budgetBrl;
  // Bar scale: the budget, or the total when there is no budget or the build goes over it.
  const scale = Math.max(budget ?? 0, spent) || 1;
  const left = budget != null ? budget - spent : null;
  const shade = (category: Category) => SHADES[rows.findIndex((row) => row.category === category) % SHADES.length];

  return (
    <div>
      <dl className="grid grid-cols-3 gap-3 text-center">
        <Stat label="Orçamento">{budget != null ? <Price amount={budget} /> : "—"}</Stat>
        <Stat label="Gasto" strong>
          <Price amount={spent} />
        </Stat>
        <Stat label={left != null && left < 0 ? "Acima" : "Sobra"} tone={left != null && left < 0 ? "bad" : undefined}>
          {left != null ? <Price amount={Math.abs(left)} /> : "—"}
        </Stat>
      </dl>

      <div className="mt-4">
        <div className="flex h-3 overflow-hidden rounded-full bg-surface-muted" role="img" aria-label="Distribuição do orçamento entre as peças">
          {rows
            .filter((item) => cost(item) > 0)
            .map((item) => (
              <span
                key={item.category}
                className={`h-full transition-opacity duration-200 ${hovered && hovered !== item.category ? "opacity-40" : ""}`}
                style={{ width: `${(cost(item) / scale) * 100}%`, background: shade(item.category) }}
              />
            ))}
        </div>
        {budget != null && (
          <p className="mt-1.5 flex justify-between text-xs text-subtle">
            <span>{Math.round((spent / budget) * 100)}% do orçamento usado</span>
            {left != null && left > 0 && <span>{Math.round((left / budget) * 100)}% livre</span>}
          </p>
        )}
      </div>

      <ul className="mt-5 grid gap-x-8 md:grid-cols-2">
        {rows.map((item) => {
          const value = cost(item);
          const share = spent > 0 ? (value / spent) * 100 : 0;
          const active = selected === item.category || hovered === item.category;
          return (
            <li key={item.category} className="border-b border-border">
              <button
                type="button"
                onClick={() => onSelect(item.category)}
                aria-pressed={selected === item.category}
                className={`group flex w-full items-center gap-3 rounded-lg px-2 py-2.5 text-left transition-colors hover:bg-accent-soft/30 ${active ? "bg-accent-soft/30" : ""}`}
              >
                <IsoPart category={item.category} active={selected === item.category} className="size-10 shrink-0" />
                <span className="min-w-0 flex-1">
                  <span className="flex items-baseline justify-between gap-2">
                    <span className="truncate text-xs font-semibold tracking-wide text-accent uppercase">{item.categoryLabel}</span>
                    <span className="shrink-0 text-sm font-semibold tabular-nums">
                      {item.owned ? <span className="text-ok">Você já tem</span> : item.price ? <Price amount={value} /> : <span className="text-subtle">Sem preço</span>}
                    </span>
                  </span>
                  <span className={`mt-0.5 block truncate text-xs transition-colors ${active ? "text-foreground" : "text-muted"}`}>{item.component.name}</span>
                  <span className="mt-1.5 flex items-center gap-2">
                    <span className="h-1 flex-1 overflow-hidden rounded-full bg-surface-muted">
                      <span className="block h-full rounded-full" style={{ width: `${share}%`, background: shade(item.category) }} />
                    </span>
                    <span className="w-9 shrink-0 text-right text-xs text-subtle tabular-nums">{Math.round(share)}%</span>
                  </span>
                </span>
              </button>
            </li>
          );
        })}
      </ul>

      <div className="mt-3 flex items-baseline justify-between border-t border-border pt-3">
        <span className="text-sm font-semibold">Total</span>
        <span className="font-display text-lg font-bold tabular-nums">
          <Price amount={spent} />
        </span>
      </div>
      {totals.pricesAreExamples && <p className="mt-1 text-right text-xs text-subtle">Preços fictícios</p>}
    </div>
  );
}

function Stat({ label, strong, tone, children }: { label: string; strong?: boolean; tone?: "bad"; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border px-2 py-3">
      <dt className="text-[11px] font-semibold tracking-wide text-accent uppercase">{label}</dt>
      <dd className={`mt-1 text-base font-semibold whitespace-nowrap tabular-nums sm:text-lg ${strong ? "text-foreground" : ""} ${tone === "bad" ? "text-bad" : ""}`}>{children}</dd>
    </div>
  );
}
