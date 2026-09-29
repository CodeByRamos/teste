"use client";

import { useState, type ReactNode } from "react";
import { brl, signedBrl, timeAgo } from "@/lib/format";
import type { Alternative, BuildItem, BuildView as Build, Category } from "@/lib/types";
import { CompatibilityPanel } from "./compatibility-panel";
import { FuturePanel } from "./future-panel";
import { ChevronIcon, ExternalIcon, InfoIcon, SparkIcon, XIcon } from "./icons";
import { PartsMap } from "./parts-map";
import { IsoPart, PcDiagram } from "./pc-diagram";
import { Build3d } from "./pc3d/build-3d";
import { Collapse, StatusBadge } from "./ui";

export function BuildView({
  build,
  title = "Seu PC",
  subtitle,
  actions,
  extra,
  onSwap,
  busy = false,
}: {
  build: Build;
  title?: string;
  subtitle?: ReactNode;
  actions?: ReactNode;
  /** Page-specific sections shown after the build analysis (e.g. budget comparison). */
  extra?: ReactNode;
  onSwap?: (item: BuildItem, alternative: Alternative) => void;
  busy?: boolean;
}) {
  const [selected, setSelected] = useState<Category | null>(null);
  const [view, setView] = useState<"diagram" | "3d">("diagram");
  const { totals } = build;
  const stockCooler = !build.items.some((item) => item.category === "CPU_COOLER");
  const budgetShare = totals.budgetBrl ? Math.min(100, (totals.totalBrl / totals.budgetBrl) * 100) : null;
  const somethingToBuy = build.items.some((item) => !item.owned);

  const [partsView, setPartsView] = useState<"sheet" | "map">("map");
  const [expanded, setExpanded] = useState<Set<Category>>(new Set());
  const selectedItem = build.items.find((item) => item.category === selected);

  /** Picked on the diagram, the 3D view or the map: open the part's details and bring them into view. */
  function select(category: Category) {
    setSelected(category);
    if (partsView === "sheet") setExpanded(new Set([category]));
    setTimeout(
      () =>
        document
          .getElementById(partsView === "map" ? "detalhes-peca" : `peca-${category}`)
          ?.scrollIntoView({ behavior: "smooth", block: partsView === "map" ? "nearest" : "start" }),
      partsView === "map" ? 0 : 320,
    );
  }

  function toggleOnMap(category: Category) {
    if (selected === category) setSelected(null);
    else select(category);
  }

  /**
   * The sheet opens one part at a time: opening a part closes the one that was open, then brings the opened
   * part to the top of the screen for reading (once the other part has finished closing, so the target is still).
   */
  function toggleOnSheet(category: Category) {
    const opening = !expanded.has(category);
    setExpanded(opening ? new Set([category]) : new Set());
    setSelected(category);
    if (opening) {
      setTimeout(() => document.getElementById(`peca-${category}`)?.scrollIntoView({ behavior: "smooth", block: "start" }), 320);
    }
  }

  return (
    <div className={busy ? "pointer-events-none opacity-60 transition-opacity" : "transition-opacity"} aria-busy={busy}>
      {/* Summary: plain text on the page, no panel. */}
      <header className="flex flex-col gap-6 border-b border-border pb-8 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
          {subtitle && <div className="mt-1.5 text-muted">{subtitle}</div>}
          {build.needs && (
            <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted" aria-label="Seu pedido">
              {build.needs.useCases.map((use) => (
                <li key={use.value}>
                  {use.label}
                  {build.needs?.primaryUse?.value === use.value && <span className="text-accent"> · prioridade</span>}
                </li>
              ))}
              {build.needs.useCases.some((use) => use.value.startsWith("GAMING")) && <li>{build.needs.resolution.label}</li>}
            </ul>
          )}
        </div>
        <div className="md:text-right">
          {somethingToBuy && (
            <>
              <p className="text-xs text-subtle">{totalLabel(totals.pricesAreExamples, build.items)}</p>
              <p className="font-display text-3xl font-bold tracking-tight tabular-nums sm:text-4xl">{brl(totals.totalBrl)}</p>
            </>
          )}
          <div className="mt-2 flex flex-wrap items-center gap-3 md:justify-end">
            {totals.budgetBrl != null && budgetShare != null && (
              <span className="flex items-center gap-2 text-sm text-muted">
                <span className="h-1 w-20 overflow-hidden rounded-full bg-border" aria-hidden>
                  <span
                    className={`block h-full rounded-full ${totals.withinBudget ? "bg-primary" : "bg-bad"}`}
                    style={{ width: `${budgetShare}%` }}
                  />
                </span>
                {totals.withinBudget ? "Dentro do" : "Acima do"} orçamento de {brl(totals.budgetBrl)}
              </span>
            )}
            <StatusBadge status={build.compatibility.overall} />
          </div>
        </div>
      </header>

      <div className="mt-8 grid gap-10 lg:grid-cols-[minmax(260px,340px)_1fr]">
        {/* Visual */}
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <ViewTabs
            label="Visualização"
            options={[
              { value: "diagram", label: "Diagrama" },
              { value: "3d", label: "3D" },
            ]}
            value={view}
            onChange={setView}
          />
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
          {view === "3d" && (
            <p className="mt-3 text-xs leading-relaxed text-subtle">
              Modelo 3D montado com as medidas de cada peça (quando a base de dados informa). Arraste para girar e clique numa peça.
            </p>
          )}
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
          {(!totals.allPriced || build.notes.length > 0) && (
            <div className="mt-6">
              <p className="flex items-center gap-2 text-sm font-semibold">
                <InfoIcon className="size-4 text-accent" /> Observações
              </p>
              <ul className="mt-2 space-y-2 text-sm leading-relaxed text-muted">
                {!totals.allPriced && <li>Algumas peças ainda não têm preço disponível e não entram no total.</li>}
                {build.notes.map((note) => (
                  <li key={note}>{note}</li>
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
            <ViewTabs
              label="Mostrar peças como"
              options={[
                { value: "sheet", label: "Ficha" },
                { value: "map", label: "Mapa" },
              ]}
              value={partsView}
              onChange={setPartsView}
            />
            {partsView === "sheet" ? (
              <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface">
                {build.items.map((item) => (
                  <PartRow
                    key={item.component.id}
                    item={item}
                    expanded={expanded.has(item.category)}
                    selected={selected === item.category}
                    onToggle={() => toggleOnSheet(item.category)}
                    onSwap={onSwap}
                    performanceDisclaimer={build.disclaimers.performance}
                  />
                ))}
              </ul>
            ) : (
              <>
                <PartsMap items={build.items} findings={build.compatibility.findings} selected={selected} onSelect={toggleOnMap} />
                {selectedItem ? (
                  <PartDetails
                    key={selectedItem.category}
                    item={selectedItem}
                    onClose={() => setSelected(null)}
                    onSwap={onSwap}
                    performanceDisclaimer={build.disclaimers.performance}
                  />
                ) : (
                  <p className="mt-3 text-sm text-subtle">Toque numa peça do mapa para ver por que ela foi escolhida e suas especificações.</p>
                )}
              </>
            )}
          </section>

          <CompatibilityPanel compatibility={build.compatibility} />

          {build.future && <FuturePanel future={build.future} />}

          {extra}

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

/** Says plainly whether the total rests on real store prices, fictitious ones, or a mix. */
function totalLabel(pricesAreExamples: boolean, items: BuildItem[]) {
  if (!pricesAreExamples) return "Total estimado";
  const anyReal = items.some((item) => item.price && !item.price.isExample && !item.owned);
  return anyReal ? "Total estimado (inclui preços fictícios)" : "Total estimado (preços fictícios)";
}

function PartDetails({
  item,
  onClose,
  onSwap,
  performanceDisclaimer,
}: {
  item: BuildItem;
  onClose: () => void;
  onSwap?: (item: BuildItem, alternative: Alternative) => void;
  performanceDisclaimer: string;
}) {
  return (
    <div
      id="detalhes-peca"
      role="region"
      aria-labelledby="detalhes-peca-titulo"
      className="mt-4 scroll-mt-28 animate-[panel-in_0.3s_ease-out] rounded-2xl border border-border bg-surface"
    >
      <div className="flex items-start gap-4 border-b border-border px-5 py-4 sm:px-6">
        <IsoPart category={item.category} active className="size-14 shrink-0" />
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium tracking-wide text-muted uppercase">{item.categoryLabel}</p>
          <h3 id="detalhes-peca-titulo" className="mt-0.5 font-semibold text-pretty">
            {item.component.name}
          </h3>
          <p className="mt-1 text-sm text-muted">{item.explanation.whatItIs}</p>
        </div>
        <div className="shrink-0 text-right">
          {item.owned ? (
            <span className="rounded-full bg-ok-soft px-2.5 py-1 text-xs font-semibold text-ok">Você já tem</span>
          ) : item.price ? (
            <>
              <span className="block font-semibold tabular-nums">{brl(item.price.amountBrl)}</span>
              <span className="block text-xs text-subtle">{item.price.isExample ? "fictício" : item.price.storeName}</span>
            </>
          ) : (
            <span className="text-sm text-subtle">Sem preço</span>
          )}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="-mt-1 -mr-2 rounded-lg p-1.5 text-subtle hover:bg-surface-muted hover:text-foreground"
          aria-label="Fechar detalhes"
        >
          <XIcon className="size-4" />
        </button>
      </div>

      <div className="px-5 py-5 sm:px-6">
        <PartBody item={item} onSwap={onSwap} performanceDisclaimer={performanceDisclaimer} />
      </div>
    </div>
  );
}

/** One row of the parts sheet: the part in a line, and everything about it when expanded. */
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
  return (
    <li id={`peca-${item.category}`} className="scroll-mt-6">
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={expanded}
        aria-controls={panelId}
        className="group flex w-full items-start gap-4 px-5 py-4 text-left transition-colors hover:bg-accent-soft/30 sm:px-6"
      >
        <ChevronIcon
          className={`mt-3 size-4 shrink-0 text-subtle transition-[rotate,translate,color] duration-200 group-hover:text-accent ${expanded ? "rotate-90" : "group-hover:translate-x-0.5"}`}
        />
        <IsoPart category={item.category} active={selected} className="size-11 shrink-0" />
        <span className="min-w-0 flex-1">
          <span className="block text-xs font-medium tracking-wide text-muted uppercase">{item.categoryLabel}</span>
          <span className="mt-0.5 block font-semibold text-pretty">{item.component.name}</span>
        </span>
        <span className="shrink-0 text-right">
          {item.owned ? (
            <span className="rounded-full bg-ok-soft px-2.5 py-1 text-xs font-semibold text-ok">Você já tem</span>
          ) : item.price ? (
            <>
              <span className="block font-semibold tabular-nums">{brl(item.price.amountBrl)}</span>
              <span className="block text-xs text-subtle">{item.price.isExample ? "fictício" : item.price.storeName}</span>
            </>
          ) : (
            <span className="text-sm text-subtle">Sem preço</span>
          )}
        </span>
      </button>

      <Collapse open={expanded} id={panelId} className="px-5 pb-6 pl-13 sm:px-6 sm:pl-14">
        <PartBody item={item} onSwap={onSwap} performanceDisclaimer={performanceDisclaimer} />
      </Collapse>
    </li>
  );
}

/**
 * What we know about a part, in short sections: its specs first, then why we picked it and other options.
 * The data source stays collapsed for whoever wants it.
 */
function PartBody({
  item,
  onSwap,
  performanceDisclaimer,
}: {
  item: BuildItem;
  onSwap?: (item: BuildItem, alternative: Alternative) => void;
  performanceDisclaimer: string;
}) {
  const missingFields = item.component.quality.issues.filter((issue) => issue.kind !== "NORMALIZED");
  return (
    <div className="space-y-6">
      <Section title="Especificações">
        <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
          {item.specs.map((spec) => (
            <div key={spec.label} className="flex justify-between gap-3 border-b border-border pb-2">
              <dt className="text-muted">{spec.label}</dt>
              <dd className={`text-right font-medium ${spec.value === "Não informado" ? "text-subtle" : ""}`}>{spec.value}</dd>
            </div>
          ))}
        </dl>
      </Section>

      {item.price && !item.price.isExample && item.price.url && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-surface-muted/60 px-4 py-3">
          <p className="text-sm text-muted">
            {brl(item.price.amountBrl)} na {item.price.storeName} · visto {timeAgo(item.price.observedAt)}
          </p>
          <a
            href={item.price.url}
            target="_blank"
            rel="sponsored nofollow noopener noreferrer"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-accent underline-offset-2 hover:underline"
          >
            Ver na loja
            <ExternalIcon className="size-3.5" />
          </a>
          <p className="w-full text-xs text-subtle">
            O preço pode ter mudado. Alguns links são de parceiros: a loja pode nos pagar uma comissão, sem custo extra para você.
          </p>
        </div>
      )}

      <div className="flex gap-3.5 rounded-xl border border-border bg-surface-muted/40 p-4">
        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-accent-soft text-accent">
          <SparkIcon className="size-4.5" />
        </span>
        <div className="min-w-0">
          <p className="text-xs font-semibold tracking-wide text-muted uppercase">
            {item.owned ? "Sobre a sua peça" : "Por que escolhemos esta"}
          </p>
          <p className="mt-1 leading-relaxed">{item.explanation.reason}</p>
        </div>
      </div>

      {item.alternatives.length > 0 && (
        <Section title="Outras opções">
          <ul className="space-y-2">
            {item.alternatives.map((alternative) => {
              const cheaper = alternative.direction === "CHEAPER";
              return (
                <li
                  key={alternative.component.id}
                  className="flex flex-col gap-3 rounded-xl border border-border p-3 sm:flex-row sm:items-center sm:gap-4"
                >
                  <span
                    className={`w-fit shrink-0 rounded-full px-2.5 py-1 text-center text-xs font-semibold tabular-nums sm:w-44 ${cheaper ? "bg-ok-soft text-ok" : "bg-accent-soft text-accent"}`}
                  >
                    {cheaper ? "Mais barata" : "Mais forte"} · {signedBrl(alternative.priceDeltaBrl)}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-pretty">{alternative.component.name}</p>
                    <p className="mt-0.5 text-sm text-muted">{alternative.impact}</p>
                  </div>
                  {onSwap && (
                    <button
                      type="button"
                      onClick={() => onSwap(item, alternative)}
                      className="shrink-0 self-start rounded-lg bg-accent-soft px-3 py-1.5 text-sm font-semibold text-accent transition-[filter] hover:brightness-125 sm:self-center"
                    >
                      Trocar
                    </button>
                  )}
                </li>
              );
            })}
          </ul>
          {(item.category === "CPU" || item.category === "GPU") && (
            <p className="mt-2 flex gap-1.5 text-xs text-subtle">
              <InfoIcon className="size-3.5 shrink-0" /> {performanceDisclaimer}
            </p>
          )}
        </Section>
      )}

      <details className="border-t border-border pt-4 text-xs text-subtle">
        <summary className="cursor-pointer hover:text-foreground">
          Sobre os dados · {Math.round(item.component.quality.score * 100)}% completos
        </summary>
        <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1">
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
        </div>
        {missingFields.length > 0 && (
          <>
            <p className="mt-2">
              {missingFields.length} {missingFields.length === 1 ? "campo" : "campos"} sem informação confiável na base:
            </p>
            <ul className="mt-1 list-disc pl-5 font-mono">
              {missingFields.map((issue) => (
                <li key={issue.field + issue.kind}>
                  {issue.field}
                  {issue.detail ? ` — ${issue.detail}` : ""}
                </li>
              ))}
            </ul>
          </>
        )}
      </details>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="mb-2 text-xs font-semibold tracking-wide text-muted uppercase">{title}</p>
      {children}
    </div>
  );
}

/** Segmented switch between two ways of showing the same thing. */
function ViewTabs<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div className="mb-3 flex gap-1 rounded-xl bg-surface-muted p-1 text-sm" role="tablist" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          role="tab"
          aria-selected={value === option.value}
          onClick={() => onChange(option.value)}
          className={`flex-1 rounded-lg px-3 py-1.5 font-medium transition-colors ${value === option.value ? "bg-primary font-semibold text-accent-foreground shadow-sm" : "text-muted hover:text-foreground"}`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
