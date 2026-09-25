"use client";

import { useState, type ReactNode } from "react";
import { brl, signedBrl } from "@/lib/format";
import type { Alternative, BuildItem, BuildView as Build, Category } from "@/lib/types";
import { CompatibilityPanel } from "./compatibility-panel";
import { ChevronIcon, ExternalIcon, InfoIcon, SparkIcon } from "./icons";
import { PcDiagram } from "./pc-diagram";
import { Build3d } from "./pc3d/build-3d";
import { Notice, StatusBadge } from "./ui";

export function BuildView({
  build,
  title = "Seu PC",
  subtitle,
  actions,
  onSwap,
  busy = false,
}: {
  build: Build;
  title?: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  onSwap?: (item: BuildItem, alternative: Alternative) => void;
  busy?: boolean;
}) {
  const [selected, setSelected] = useState<Category | null>(null);
  const [view, setView] = useState<"diagram" | "3d">("diagram");
  const [expanded, setExpanded] = useState<Set<Category>>(new Set());
  const { totals } = build;
  const stockCooler = !build.items.some((item) => item.category === "CPU_COOLER");
  const budgetShare = totals.budgetBrl ? Math.min(100, (totals.totalBrl / totals.budgetBrl) * 100) : null;
  const somethingToBuy = build.items.some((item) => !item.owned);

  function select(category: Category) {
    setSelected(category);
    setExpanded((current) => new Set(current).add(category));
    document.getElementById(`peca-${category}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  function toggle(category: Category) {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(category)) next.delete(category);
      else next.add(category);
      return next;
    });
    setSelected(category);
  }

  return (
    <div className={busy ? "pointer-events-none opacity-60 transition-opacity" : "transition-opacity"} aria-busy={busy}>
      {/* Summary */}
      <header className="flex flex-col gap-6 border-b border-border pb-8 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
          {subtitle && <div className="mt-2 text-muted">{subtitle}</div>}
          {build.needs && (
            <ul className="mt-4 flex flex-wrap gap-2" aria-label="Seu pedido">
              {build.needs.useCases.map((use) => (
                <li key={use.value} className="rounded-full bg-surface-muted px-3 py-1 text-sm">
                  {use.label}
                  {build.needs?.primaryUse?.value === use.value && <span className="text-muted"> · prioridade</span>}
                </li>
              ))}
              {build.needs.useCases.some((use) => use.value.startsWith("GAMING")) && (
                <li className="rounded-full bg-surface-muted px-3 py-1 text-sm">{build.needs.resolution.label}</li>
              )}
            </ul>
          )}
        </div>
        <div className="md:text-right">
          {somethingToBuy && (
            <>
              <p className="text-sm text-muted">{totals.pricesAreExamples ? "Total estimado (preços fictícios)" : "Total estimado"}</p>
              <p className="text-4xl font-semibold tracking-tight tabular-nums">{brl(totals.totalBrl)}</p>
            </>
          )}
          {totals.budgetBrl != null && budgetShare != null && (
            <div className="mt-2 md:ml-auto md:w-64">
              <div className="h-1.5 overflow-hidden rounded-full bg-border" aria-hidden>
                <div
                  className={`h-full rounded-full ${totals.withinBudget ? "bg-accent" : "bg-bad"}`}
                  style={{ width: `${budgetShare}%` }}
                />
              </div>
              <p className="mt-1.5 text-sm text-muted">
                {totals.withinBudget ? "Dentro do" : "Acima do"} orçamento de {brl(totals.budgetBrl)}
              </p>
            </div>
          )}
          <div className="mt-3">
            <StatusBadge status={build.compatibility.overall} />
          </div>
        </div>
      </header>

      <div className="mt-6 space-y-3">
        {build.disclaimers.prices && <Notice tone="warn">{build.disclaimers.prices}</Notice>}
        {!totals.allPriced && <Notice tone="warn">Algumas peças ainda não têm preço disponível e não entram no total.</Notice>}
        {build.notes.map((note) => (
          <Notice key={note}>{note}</Notice>
        ))}
      </div>

      <div className="mt-8 grid gap-10 lg:grid-cols-[minmax(260px,340px)_1fr]">
        {/* Visual */}
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="mb-3 flex gap-1 rounded-xl bg-surface-muted p-1 text-sm" role="tablist" aria-label="Visualização">
            {(["diagram", "3d"] as const).map((mode) => (
              <button
                key={mode}
                role="tab"
                aria-selected={view === mode}
                onClick={() => setView(mode)}
                className={`flex-1 rounded-lg px-3 py-1.5 font-medium transition-colors ${view === mode ? "bg-surface text-foreground shadow-sm" : "text-muted hover:text-foreground"}`}
              >
                {mode === "diagram" ? "Diagrama" : "3D"}
              </button>
            ))}
          </div>
          {view === "diagram" ? (
            <div className="mx-auto max-w-[260px] rounded-2xl border border-border bg-surface p-4 lg:max-w-none">
              <PcDiagram
                items={build.items}
                findings={build.compatibility.findings}
                selected={selected}
                onSelect={select}
                stockCooler={stockCooler}
              />
            </div>
          ) : (
            <div className="h-[380px] overflow-hidden rounded-2xl border border-border bg-surface">
              <Build3d items={build.items} selected={selected} onSelect={select} />
            </div>
          )}
          <p className="mt-3 text-xs leading-relaxed text-subtle">
            {view === "diagram"
              ? "Visualização esquemática: toque em uma peça para ver a explicação."
              : "Modelo 3D montado com as medidas de cada peça (quando a base de dados informa). Arraste para girar e clique numa peça."}
          </p>
          {build.requirements.length > 0 && (
            <div className="mt-6">
              <p className="flex items-center gap-2 text-sm font-semibold">
                <SparkIcon className="size-4 text-accent" /> Como entendemos seu pedido
              </p>
              <ul className="mt-2 space-y-2 text-sm leading-relaxed text-muted">
                {build.requirements.map((requirement) => (
                  <li key={requirement}>{requirement}</li>
                ))}
              </ul>
            </div>
          )}
        </aside>

        {/* Parts */}
        <div className="min-w-0 space-y-8">
          <section aria-labelledby="pecas-title">
            <h2 id="pecas-title" className="sr-only">
              Peças
            </h2>
            <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface">
              {build.items.map((item) => (
                <PartRow
                  key={item.component.id}
                  item={item}
                  expanded={expanded.has(item.category)}
                  selected={selected === item.category}
                  onToggle={() => toggle(item.category)}
                  onSwap={onSwap}
                  performanceDisclaimer={build.disclaimers.performance}
                />
              ))}
            </ul>
          </section>

          <CompatibilityPanel compatibility={build.compatibility} />

          {actions && <div className="flex flex-wrap gap-3">{actions}</div>}

          <p className="text-xs leading-relaxed text-subtle">
            Dados técnicos: {build.dataSource.name} (versão {build.dataSource.version.slice(0, 7)}), sob a{" "}
            <a href={build.dataSource.licenseUrl} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2">
              {build.dataSource.license}
            </a>
            . Motores: {build.engines.recommendation}, {build.engines.compatibility}.
          </p>
        </div>
      </div>
    </div>
  );
}

function PartRow({
  item,
  expanded,
  selected,
  onToggle,
  onSwap,
  performanceDisclaimer,
}: {
  item: BuildItem;
  expanded: boolean;
  selected: boolean;
  onToggle: () => void;
  onSwap?: (item: BuildItem, alternative: Alternative) => void;
  performanceDisclaimer: string;
}) {
  const panelId = `detalhes-${item.category}-${item.component.id}`;
  const missingFields = item.component.quality.issues.filter((issue) => issue.kind !== "NORMALIZED");
  return (
    <li id={`peca-${item.category}`} className={`scroll-mt-28 transition-colors ${selected ? "bg-accent-soft/40" : ""}`}>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        aria-controls={panelId}
        className="flex w-full items-start gap-4 px-5 py-4 text-left sm:px-6"
      >
        <ChevronIcon className={`mt-1 size-4 shrink-0 text-subtle transition-transform ${expanded ? "rotate-90" : ""}`} />
        <span className="min-w-0 flex-1">
          <span className="block text-xs font-medium tracking-wide text-muted uppercase">{item.categoryLabel}</span>
          <span className="mt-0.5 block font-semibold text-pretty">{item.component.name}</span>
          {!expanded && <span className="mt-1 line-clamp-1 text-sm text-muted">{item.explanation.reason}</span>}
        </span>
        <span className="shrink-0 text-right">
          {item.owned ? (
            <span className="rounded-full bg-ok-soft px-2.5 py-1 text-xs font-semibold text-ok">Você já tem</span>
          ) : item.price ? (
            <>
              <span className="block font-semibold tabular-nums">{brl(item.price.amountBrl)}</span>
              {item.price.isExample && <span className="block text-xs text-subtle">fictício</span>}
            </>
          ) : (
            <span className="text-sm text-subtle">Sem preço</span>
          )}
        </span>
      </button>

      {expanded && (
        <div id={panelId} className="space-y-5 px-5 pb-6 pl-13 sm:px-6 sm:pl-14">
          <Layer title="O que é">{item.explanation.whatItIs}</Layer>
          <Layer title="Por que importa">{item.explanation.whyItMatters}</Layer>
          <div className="rounded-xl bg-accent-soft/60 p-4">
            <p className="text-sm font-semibold">{item.owned ? "Sobre esta peça" : "Por que escolhemos"}</p>
            <p className="mt-1 leading-relaxed">{item.explanation.reason}</p>
          </div>

          <div>
            <p className="text-sm font-semibold">Especificações</p>
            <dl className="mt-2 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
              {item.specs.map((spec) => (
                <div key={spec.label} className="flex justify-between gap-3 border-b border-border pb-2">
                  <dt className="text-muted">{spec.label}</dt>
                  <dd className={`text-right font-medium ${spec.value === "Não informado" ? "text-subtle" : ""}`}>{spec.value}</dd>
                </div>
              ))}
            </dl>
          </div>

          {item.alternatives.length > 0 && (
            <div>
              <p className="text-sm font-semibold">Alternativas</p>
              <ul className="mt-2 divide-y divide-border rounded-xl border border-border">
                {item.alternatives.map((alternative) => (
                  <li key={alternative.component.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                    <div className="min-w-0 flex-1">
                      <p className="text-xs font-medium text-muted uppercase">
                        {alternative.direction === "CHEAPER" ? "Mais em conta" : "Um degrau acima"}
                      </p>
                      <p className="font-medium text-pretty">{alternative.component.name}</p>
                      <p className="mt-0.5 text-sm text-muted">{alternative.impact}</p>
                    </div>
                    <div className="flex items-center gap-3 sm:flex-col sm:items-end">
                      <span
                        className={`font-semibold tabular-nums ${alternative.priceDeltaBrl < 0 ? "text-ok" : "text-foreground"}`}
                      >
                        {signedBrl(alternative.priceDeltaBrl)}
                      </span>
                      {onSwap && (
                        <button
                          type="button"
                          onClick={() => onSwap(item, alternative)}
                          className="rounded-lg border border-border-strong px-3 py-1.5 text-sm font-medium hover:bg-surface-muted"
                        >
                          Trocar
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              {(item.category === "CPU" || item.category === "GPU") && (
                <p className="mt-2 flex gap-1.5 text-xs text-subtle">
                  <InfoIcon className="size-3.5 shrink-0" /> {performanceDisclaimer}
                </p>
              )}
            </div>
          )}

          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-subtle">
            <span>Fonte: {item.component.source.name}</span>
            {item.component.source.recordUrl && (
              <a
                href={item.component.source.recordUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 underline underline-offset-2 hover:text-foreground"
              >
                Ver registro original <ExternalIcon className="size-3" />
              </a>
            )}
            <span>Dados completos: {Math.round(item.component.quality.score * 100)}%</span>
            {missingFields.length > 0 && (
              <details className="w-full">
                <summary className="cursor-pointer">
                  {missingFields.length} {missingFields.length === 1 ? "campo" : "campos"} sem informação confiável na base
                </summary>
                <ul className="mt-1 list-disc pl-5 font-mono">
                  {missingFields.map((issue) => (
                    <li key={issue.field + issue.kind}>
                      {issue.field}
                      {issue.detail ? ` — ${issue.detail}` : ""}
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </div>
        </div>
      )}
    </li>
  );
}

function Layer({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-sm font-semibold">{title}</p>
      <p className="mt-1 leading-relaxed text-muted">{children}</p>
    </div>
  );
}
