"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { brl, signedBrl, timeAgo } from "@/lib/format";
import type { Alternative, BuildItem, BuildView as Build, Category } from "@/lib/types";
import { CompatibilityPanel } from "./compatibility-panel";
import { FuturePanel } from "./future-panel";
import { ArrowIcon, BoltIcon, ChevronIcon, ExternalIcon, InfoIcon, XIcon } from "./icons";
import { PartsMap } from "./parts-map";
import { SetupReview } from "./setup-review";
import { BudgetPlanner } from "./budget-planner";
import { IsoPart, PcDiagram } from "./pc-diagram";
import { Build3d } from "./pc3d/build-3d";
import { Callout, Collapse, Price, StatusBadge } from "./ui";

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
  /** Part under the pointer in the map or the sheet, highlighted in the diagram too. */
  const [hovered, setHovered] = useState<Category | null>(null);
  const [view, setView] = useState<"diagram" | "3d">("diagram");
  const { totals } = build;
  const budgetShare = totals.budgetBrl ? Math.min(100, (totals.totalBrl / totals.budgetBrl) * 100) : null;
  const somethingToBuy = build.items.some((item) => !item.owned);

  const [partsView, setPartsView] = useState<"sheet" | "map" | "about">("map");
  /** The result reads in two pages: 1) the parts and whether they work together, 2) the money. */
  const [page, setPage] = useState<1 | 2>(1);

  function goToPage(next: 1 | 2) {
    setPage(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }
  const [expanded, setExpanded] = useState<Set<Category>>(new Set());
  const selectedItem = build.items.find((item) => item.category === selected);
  const signature = buildSignature(build.items) ?? title;

  /** Picked on the diagram, the 3D view or the map: open the part's details and bring them into view. */
  function select(category: Category) {
    setSelected(category);
    if (partsView === "sheet") setExpanded(new Set([category]));
    setTimeout(
      () =>
        document.getElementById(partsView === "map" ? "detalhes-peca" : `peca-${category}`)?.scrollIntoView({
          behavior: "smooth",
          block: partsView === "map" ? "nearest" : "start",
        }),
      partsView === "map" ? 0 : 320,
    );
  }

  // A stable handle for the 3D view, so re-renders (e.g. hover highlights) don't re-render the scene.
  const latestSelect = useRef(select);
  useEffect(() => {
    latestSelect.current = select;
  });
  const selectRef = useCallback((category: Category) => latestSelect.current(category), []);

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
          <p className="text-sm text-muted">
            <span className="font-semibold text-accent">{title}</span>
            {build.needs && (
              <>
                {" "}
                para {build.needs.useCases.map((use) => use.label.toLowerCase()).join(", ")}
                {build.needs.useCases.some((use) => use.value.startsWith("GAMING")) && <> em {build.needs.resolution.label}</>}
              </>
            )}
          </p>
          <h1 className="mt-2 max-w-[22ch] text-3xl leading-[1.1] font-bold tracking-tight text-balance sm:text-5xl">{signature}</h1>
          {subtitle && <div className="mt-3 text-sm text-subtle">{subtitle}</div>}
        </div>
        <div className="md:text-right">
          {somethingToBuy && (
            <>
              <p className="text-xs text-subtle">{totalLabel(totals.pricesAreExamples, build.items)}</p>
              <p className="font-display text-3xl font-bold tracking-tight tabular-nums sm:text-4xl">
                <Price amount={totals.totalBrl} />
              </p>
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
          {actions && <div className="mt-4 flex flex-wrap items-center gap-2 md:justify-end">{actions}</div>}
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
            <div className="mx-auto max-w-[260px] panel rounded-2xl p-4 lg:max-w-none">
              <PcDiagram
                hovered={hovered}
                items={build.items}
                findings={build.compatibility.findings}
                selected={selected}
                onSelect={select}
                stockCooler={!build.items.some((item) => item.category === "CPU_COOLER")}
              />
            </div>
          ) : (
            <div className="h-[380px] overflow-hidden panel rounded-2xl">
              <Build3d items={build.items} selected={selected} onSelect={selectRef} />
            </div>
          )}
          {view === "3d" && (
            <p className="mt-3 text-xs leading-relaxed text-subtle">
              Modelo 3D montado com as medidas de cada peça (quando a base de dados informa). Arraste para girar e clique numa peça.
            </p>
          )}
          <SectionIndex
            key={page}
            sections={
              page === 1
                ? [
                    { id: "pecas", label: "Peças" },
                    { id: "compatibilidade", label: "Compatibilidade" },
                    ...(build.future ? [{ id: "futuro", label: "Pensando no futuro" }] : []),
                  ]
                : [
                    { id: "gastos", label: "Planner de gastos" },
                    ...(extra
                      ? [
                          {
                            id: "orcamento",
                            label: "E se eu mudar o orçamento?",
                          },
                        ]
                      : []),
                  ]
            }
          />
        </aside>

        {/* Parts */}
        <div className="min-w-0 space-y-8">
          {page === 1 && (
            <>
              <section id="pecas" aria-labelledby="pecas-title" className="scroll-mt-6">
                <h2 id="pecas-title" className="sr-only">
                  Peças
                </h2>
                <ViewTabs
                  label="Mostrar peças como"
                  options={[
                    { value: "sheet", label: "Ficha" },
                    { value: "map", label: "Mapa" },
                    { value: "about", label: "Sobre o PC" },
                  ]}
                  value={partsView}
                  onChange={setPartsView}
                />
                {partsView === "about" ? (
                  <SetupReview build={build} />
                ) : partsView === "sheet" ? (
                  <ul className="divide-y divide-border overflow-hidden panel rounded-2xl">
                    {build.items.map((item) => (
                      <PartRow
                        key={item.component.id}
                        item={item}
                        expanded={expanded.has(item.category)}
                        selected={selected === item.category}
                        onToggle={() => toggleOnSheet(item.category)}
                        onHover={(on) => setHovered(on ? item.category : null)}
                        onSwap={onSwap}
                        performanceDisclaimer={build.disclaimers.performance}
                      />
                    ))}
                  </ul>
                ) : (
                  <>
                    <PartsMap
                      items={build.items}
                      findings={build.compatibility.findings}
                      selected={selected}
                      onSelect={toggleOnMap}
                      onHover={setHovered}
                    />
                    {selectedItem ? (
                      <PartDetails
                        key={selectedItem.category}
                        item={selectedItem}
                        onClose={() => setSelected(null)}
                        onSwap={onSwap}
                        performanceDisclaimer={build.disclaimers.performance}
                      />
                    ) : (
                      <p className="mt-3 text-sm text-subtle">
                        Toque numa peça do mapa para ver por que ela foi escolhida e suas especificações.
                      </p>
                    )}
                  </>
                )}
              </section>

              <CompatibilityPanel
                compatibility={build.compatibility}
                psuWatts={
                  Number.parseInt(
                    build.items.find((item) => item.category === "POWER_SUPPLY")?.specs.find((s) => s.label === "Potência")?.value ?? "",
                    10,
                  ) || null
                }
              />

              {build.future && <FuturePanel future={build.future} />}

              <PageNav
                current={1}
                label="Gastos e orçamento"
                hint="Para onde vai o dinheiro e o que muda com mais ou menos orçamento."
                onClick={() => goToPage(2)}
              />
            </>
          )}

          {page === 2 && (
            <>
              <button
                type="button"
                onClick={() => goToPage(1)}
                className="inline-flex items-center gap-1.5 text-sm font-medium text-muted transition-colors hover:text-foreground"
              >
                <ArrowIcon className="size-4 rotate-180" /> Voltar às peças
              </button>

              <section id="gastos" aria-labelledby="planner-title" className="reveal panel scroll-mt-6 rounded-2xl p-5 sm:p-6">
                <h2 id="planner-title" className="text-lg font-semibold">
                  Planner de gastos
                </h2>
                <p className="mt-2 leading-relaxed text-muted">Para onde vai cada real do seu orçamento.</p>
                <div className="mt-5">
                  <BudgetPlanner items={build.items} totals={totals} selected={selected} hovered={hovered} onSelect={select} />
                </div>
              </section>

              {extra}

              <PageNav current={2} label="Peças e compatibilidade" onClick={() => goToPage(1)} />
            </>
          )}

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

/**
 * The build named by the parts that define it: processor and graphics chip ("Ryzen 7 9850X3D + Radeon RX 7900 XT"),
 * or the processor alone when there is no graphics card.
 */
function buildSignature(items: BuildItem[]): string | null {
  const cpu = items.find((item) => item.category === "CPU")?.component.name.replace(/^(AMD|Intel)\s+/i, "");
  const gpuItem = items.find((item) => item.category === "GPU");
  const gpu = gpuItem?.specs.find((spec) => spec.label === "Chip" && spec.value !== "Não informado")?.value ?? gpuItem?.component.name;
  if (cpu && gpu) return `${cpu} + ${gpu}`;
  return cpu ?? gpu ?? null;
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
      className="mt-4 scroll-mt-28 animate-[panel-in_0.3s_ease-out] panel rounded-2xl"
    >
      <div className="flex items-start gap-4 border-b border-border px-5 py-4 sm:px-6">
        <IsoPart category={item.category} active className="size-14 shrink-0" />
        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold tracking-wide text-accent uppercase">{item.categoryLabel}</p>
          <h3 id="detalhes-peca-titulo" className="mt-0.5 font-semibold text-pretty">
            {item.component.name}
          </h3>
          <p className="mt-1 text-sm text-muted">{item.explanation.whatItIs}</p>
        </div>
        <div className="shrink-0 text-right">
          {item.owned ? (
            <span className="text-sm font-medium text-ok">Você já tem</span>
          ) : item.price ? (
            <>
              <span className="block font-semibold tabular-nums">
                <Price amount={item.price.amountBrl} />
              </span>
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
  onHover,
  onSwap,
  performanceDisclaimer,
}: {
  item: BuildItem;
  expanded: boolean;
  selected: boolean;
  onToggle: () => void;
  onHover: (on: boolean) => void;
  onSwap?: (item: BuildItem, alternative: Alternative) => void;
  performanceDisclaimer: string;
}) {
  const panelId = `detalhes-${item.category}-${item.component.id}`;
  return (
    <li id={`peca-${item.category}`} className="scroll-mt-6">
      <button
        type="button"
        onClick={onToggle}
        onPointerEnter={() => onHover(true)}
        onPointerLeave={() => onHover(false)}
        onFocus={() => onHover(true)}
        onBlur={() => onHover(false)}
        aria-expanded={expanded}
        aria-controls={panelId}
        className="group flex w-full items-start gap-4 px-5 py-4 text-left transition-colors hover:bg-accent-soft/30 sm:px-6"
      >
        <ChevronIcon
          className={`mt-3 size-4 shrink-0 text-subtle transition-[rotate,translate,color] duration-200 group-hover:text-accent ${expanded ? "rotate-90" : "group-hover:translate-x-0.5"}`}
        />
        <IsoPart category={item.category} active={selected} className="size-11 shrink-0" />
        <span className="min-w-0 flex-1">
          <span className="block text-xs font-semibold tracking-wide text-accent uppercase">{item.categoryLabel}</span>
          <span className="mt-0.5 block font-semibold text-pretty transition-colors group-hover:text-accent">{item.component.name}</span>
        </span>
        <span className="shrink-0 text-right">
          {item.owned ? (
            <span className="text-sm font-medium text-ok">Você já tem</span>
          ) : item.price ? (
            <>
              <span className="block font-semibold tabular-nums">
                <Price amount={item.price.amountBrl} />
              </span>
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
  const { headline, caveat } = splitCaveat(item.explanation.reason);
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

      <Callout
        title={item.owned ? "Sobre a sua peça" : "Por que escolhemos esta"}
        icon={<BoltIcon className="size-4.5" />}
        footnote={caveat}
      >
        {headline}
      </Callout>

      {item.alternatives.length > 0 && (
        <Section title="Outras opções">
          <ul className="space-y-2">
            {item.alternatives.map((alternative) => {
              const cheaper = alternative.direction === "CHEAPER";
              return (
                <li
                  key={alternative.component.id}
                  className="inner-card flex flex-col gap-3 rounded-xl p-3 sm:flex-row sm:items-center sm:gap-4"
                >
                  <span className="shrink-0 sm:w-36">
                    <span className="block text-sm text-muted">{cheaper ? "Mais barata" : "Mais forte"}</span>
                    <span className={`block font-semibold tabular-nums ${cheaper ? "text-ok" : "text-foreground"}`}>
                      {signedBrl(alternative.priceDeltaBrl)}
                    </span>
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
                  {issue.detail ? `: ${issue.detail}` : ""}
                </li>
              ))}
            </ul>
          </>
        )}
      </details>
    </div>
  );
}

/**
 * Pulls a trailing caveat out of the reason ("Ela não tem Wi-Fi: …", "Atenção: …") so it reads as a footnote
 * under the main argument instead of the end of the same paragraph.
 */
function splitCaveat(reason: string): {
  headline: string;
  caveat: string | null;
} {
  const match = reason.match(/^(.*?[.!])\s+((?:Ela|Ele|Não|Atenção|Observação|Obs\.)[^]*)$/);
  if (!match || !/\b(não|atenção|observação)\b/i.test(match[2])) return { headline: reason, caveat: null };
  return { headline: match[1], caveat: match[2] };
}

/**
 * "Nesta página": links to the result's sections, marking the one currently in view. Lives in the sticky column,
 * so it stays at hand on the long page.
 */
function SectionIndex({ sections }: { sections: { id: string; label: string }[] }) {
  const [active, setActive] = useState(sections[0]?.id);
  const ids = sections.map((section) => section.id).join();

  useEffect(() => {
    const visible = new Map<string, number>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) visible.set(entry.target.id, entry.isIntersecting ? entry.intersectionRatio : 0);
        const top = sections.find((section) => (visible.get(section.id) ?? 0) > 0);
        if (top) setActive(top.id);
      },
      { rootMargin: "-20% 0px -55% 0px", threshold: [0, 0.01] },
    );
    for (const section of sections) {
      const element = document.getElementById(section.id);
      if (element) observer.observe(element);
    }
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- keyed on the ids, not on array identity
  }, [ids]);

  return (
    <nav aria-label="Nesta página" className="mt-6 hidden lg:block">
      <p className="text-sm font-medium text-muted">Nesta página</p>
      <ul className="mt-2 border-l border-border">
        {sections.map((section) => (
          <li key={section.id}>
            <a
              href={`#${section.id}`}
              aria-current={active === section.id ? "location" : undefined}
              className={`-ml-px block border-l py-1.5 pl-3 text-sm transition-colors ${
                active === section.id ? "border-accent font-medium text-foreground" : "border-transparent text-muted hover:text-foreground"
              }`}
            >
              {section.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** Moves between the two pages of the result, with the step count so the reader knows where they are. */
function PageNav({ current, label, hint, onClick }: { current: 1 | 2; label: string; hint?: string; onClick: () => void }) {
  const forward = current === 1;
  return (
    <button
      type="button"
      onClick={onClick}
      className={`group flex w-full items-center gap-4 rounded-2xl border border-border p-5 text-left transition-colors hover:border-accent active:scale-[0.99] sm:p-6 ${
        forward ? "" : "flex-row-reverse text-right"
      }`}
    >
      <span className="min-w-0 flex-1">
        <span className="block text-sm text-muted">
          {forward ? "Avançar" : "Voltar"} · página {forward ? 2 : 1} de 2
        </span>
        <span className="mt-1 block text-xl font-bold tracking-tight transition-colors group-hover:text-accent">{label}</span>
        {hint && <span className="mt-1 block text-sm text-muted">{hint}</span>}
      </span>
      <span className="grid size-11 shrink-0 place-items-center rounded-full bg-primary text-accent-foreground transition-transform group-hover:translate-x-0.5">
        <ArrowIcon className={`size-5 ${forward ? "" : "rotate-180"}`} />
      </span>
    </button>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="mb-2 text-sm font-medium text-muted">{title}</p>
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
    <div className="mb-4 flex gap-6 border-b border-border text-sm" role="tablist" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          role="tab"
          aria-selected={value === option.value}
          onClick={() => onChange(option.value)}
          className={`-mb-px border-b-2 pb-2.5 font-medium transition-colors ${value === option.value ? "border-accent text-foreground" : "border-transparent text-muted hover:text-foreground"}`}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
