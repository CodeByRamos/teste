"use client";

import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { brl, brlShort } from "@/lib/format";
import type {
  AssessmentLevel,
  Category,
  Options,
  Resolution,
  SearchResult,
  UpgradeAdvice,
  UpgradePlan,
  UseCase,
} from "@/lib/types";
import { BuildView } from "./build-view";
import { ArrowIcon, CheckIcon, ChevronIcon, XIcon } from "./icons";
import { CATEGORY_LABELS, PartPicker } from "./part-picker";
import { Button, Notice } from "./ui";

const GAMING: UseCase[] = ["GAMING_COMPETITIVE", "GAMING_AAA"];
const BUDGETS = [800, 1500, 3000, 5000];
const FOCUS: { value: Category | null; label: string }[] = [
  { value: null, label: "O que der mais resultado" },
  { value: "GPU", label: "Jogos e gráficos (placa de vídeo)" },
  { value: "CPU", label: "Velocidade geral (processador)" },
  { value: "MEMORY", label: "Travamentos com muita coisa aberta (memória)" },
  { value: "STORAGE", label: "Lentidão para ligar e abrir (armazenamento)" },
];

const LEVEL: Record<AssessmentLevel, { label: string; className: string }> = {
  GOOD: { label: "Bom", className: "bg-ok-soft text-ok" },
  ENOUGH: { label: "Suficiente", className: "bg-accent-soft text-accent" },
  WEAK: { label: "Fraco", className: "bg-warn-soft text-warn" },
  BOTTLENECK: { label: "Gargalo", className: "bg-bad-soft text-bad" },
};

export function UpgradeScreen({ options, initialIds }: { options: Options; initialIds: string[] }) {
  const [parts, setParts] = useState<SearchResult[]>([]);
  const [uses, setUses] = useState<UseCase[]>([]);
  const [resolution, setResolution] = useState<Resolution | null>(null);
  const [budget, setBudget] = useState(1500);
  const [focus, setFocus] = useState<Category | null>(null);
  const [advice, setAdvice] = useState<UpgradeAdvice | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (initialIds.length === 0) return;
    let cancelled = false;
    Promise.all(initialIds.map((id) => api.component(id).catch(() => null))).then((found) => {
      if (!cancelled) setParts(found.filter((part): part is SearchResult => part !== null));
    });
    return () => {
      cancelled = true;
    };
  }, [initialIds]);

  const playsGames = uses.some((use) => GAMING.includes(use));
  const hasCore = parts.some((p) => p.category === "CPU") && parts.some((p) => p.category === "MOTHERBOARD");
  const canAsk = hasCore && uses.length > 0 && budget >= 200;

  async function ask() {
    setLoading(true);
    setError(null);
    try {
      const result = await api.upgrade(
        parts.map((part) => part.id),
        { budgetBrl: budget, useCases: uses, resolution: playsGames ? resolution : null },
        focus,
      );
      setAdvice(result);
      window.scrollTo({ top: 0 });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível analisar agora.");
    } finally {
      setLoading(false);
    }
  }

  if (advice) {
    return <AdviceView advice={advice} onBack={() => setAdvice(null)} />;
  }

  return (
    <div className="grid gap-10 lg:grid-cols-2">
      <section aria-labelledby="pc-atual">
        <h2 id="pc-atual" className="text-lg font-semibold">
          1. Seu PC hoje
        </h2>
        <p className="mt-1 text-sm text-muted">
          Informe pelo menos o processador e a placa-mãe. Quanto mais peças, melhor a análise.
        </p>
        {parts.length > 0 && (
          <ul className="mt-4 divide-y divide-border rounded-2xl border border-border bg-surface">
            {parts.map((part) => (
              <li key={part.id} className="flex items-center gap-3 px-4 py-3">
                <span className="min-w-0 flex-1">
                  <span className="block text-xs text-muted">{CATEGORY_LABELS[part.category]}</span>
                  <span className="block truncate text-sm font-medium">{part.name}</span>
                </span>
                <button
                  type="button"
                  onClick={() => setParts(parts.filter((p) => p.id !== part.id))}
                  className="rounded-lg p-1.5 text-muted hover:bg-surface-muted hover:text-foreground"
                  aria-label={`Remover ${part.name}`}
                >
                  <XIcon className="size-4" />
                </button>
              </li>
            ))}
          </ul>
        )}
        <div className="mt-4">
          <PartPicker
            excludeIds={parts.map((part) => part.id)}
            onPick={(part) =>
              setParts((current) => [
                ...current.filter((p) => p.category !== part.category || part.category === "STORAGE"),
                part,
              ])
            }
          />
        </div>
      </section>

      <section aria-labelledby="objetivo" className="space-y-8">
        <div>
          <h2 id="objetivo" className="text-lg font-semibold">
            2. O que você quer fazer com ele?
          </h2>
          <div className="mt-3 flex flex-wrap gap-2">
            {options.useCases.map((use) => {
              const active = uses.includes(use.value);
              return (
                <button
                  key={use.value}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setUses(active ? uses.filter((u) => u !== use.value) : [...uses, use.value])}
                  className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors ${active ? "border-accent bg-accent text-accent-foreground" : "border-border-strong bg-surface hover:bg-surface-muted"}`}
                >
                  {use.label}
                </button>
              );
            })}
          </div>
          {playsGames && (
            <div className="mt-4">
              <p className="text-sm font-medium">Resolução em que você joga</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {[...options.resolutions, { value: null, label: "Não sei" }].map((option) => (
                  <button
                    key={option.label}
                    type="button"
                    aria-pressed={resolution === option.value}
                    onClick={() => setResolution(option.value as Resolution | null)}
                    className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors ${resolution === option.value ? "border-accent bg-accent-soft text-accent" : "border-border-strong bg-surface hover:bg-surface-muted"}`}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <div>
          <h2 className="text-lg font-semibold">3. O que mais te incomoda?</h2>
          <div className="mt-3 divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface">
            {FOCUS.map((option) => (
              <label
                key={option.label}
                className={`flex cursor-pointer items-center gap-3 px-4 py-3 text-sm transition-colors ${focus === option.value ? "bg-accent-soft/60" : "hover:bg-surface-muted"}`}
              >
                <input
                  type="radio"
                  name="focus"
                  checked={focus === option.value}
                  onChange={() => setFocus(option.value)}
                  className="size-4 accent-[var(--accent)]"
                />
                {option.label}
              </label>
            ))}
          </div>
        </div>

        <div>
          <label htmlFor="upgrade-budget" className="text-lg font-semibold">
            4. Quanto quer gastar no upgrade?
          </label>
          <div className="mt-3 flex items-baseline gap-2 rounded-2xl border border-border bg-surface px-4 py-3">
            <span className="text-xl font-semibold text-muted">R$</span>
            <input
              id="upgrade-budget"
              type="number"
              inputMode="numeric"
              min={200}
              step={100}
              value={budget}
              onChange={(event) => setBudget(Number(event.target.value))}
              className="w-full bg-transparent text-2xl font-semibold focus:outline-none"
            />
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            {BUDGETS.map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => setBudget(value)}
                className={`rounded-full border px-3.5 py-1.5 text-sm ${budget === value ? "border-accent bg-accent-soft text-accent" : "border-border-strong hover:bg-surface-muted"}`}
              >
                {brlShort(value)}
              </button>
            ))}
          </div>
        </div>

        {error && <Notice tone="bad">{error}</Notice>}
        <div>
          <Button onClick={ask} disabled={!canAsk || loading} className="w-full">
            {loading ? "Analisando seu PC…" : "Analisar e sugerir upgrade"}
            {!loading && <ArrowIcon className="size-4" />}
          </Button>
          {!hasCore && <p className="mt-2 text-center text-xs text-subtle">Adicione o processador e a placa-mãe.</p>}
        </div>
      </section>
    </div>
  );
}

function AdviceView({ advice, onBack }: { advice: UpgradeAdvice; onBack: () => void }) {
  return (
    <div className="space-y-10">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-2xl font-semibold tracking-tight">Análise do seu PC</h2>
        <Button variant="secondary" onClick={onBack}>
          Ajustar informações
        </Button>
      </div>

      {advice.disclaimers.prices && <Notice tone="warn">{advice.disclaimers.prices}</Notice>}
      {advice.notes.map((note) => (
        <Notice key={note}>{note}</Notice>
      ))}

      <section aria-labelledby="hoje">
        <h3 id="hoje" className="text-lg font-semibold">
          Como está hoje
        </h3>
        <ul className="mt-3 divide-y divide-border rounded-2xl border border-border bg-surface">
          {advice.assessment.map((item, index) => (
            <li key={item.category + index} className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-start sm:gap-4">
              <span className={`w-fit shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${LEVEL[item.level].className}`}>
                {LEVEL[item.level].label}
              </span>
              <div className="min-w-0">
                <p className="text-xs font-medium text-muted uppercase">{item.categoryLabel}</p>
                <p className="font-medium">
                  {item.title}
                  {item.component && <span className="font-normal text-muted"> · {item.component.name}</span>}
                </p>
                <p className="mt-0.5 text-sm leading-relaxed text-muted">{item.explanation}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>

      {advice.recommended ? (
        <section aria-labelledby="recomendado">
          <h3 id="recomendado" className="text-lg font-semibold">
            O que recomendamos
          </h3>
          <PlanCard plan={advice.recommended} featured performance={advice.disclaimers.performance} />
        </section>
      ) : (
        <Notice>Não encontramos um upgrade que valha a pena com essas condições.</Notice>
      )}

      {advice.alternatives.length > 0 && (
        <section aria-labelledby="outras">
          <h3 id="outras" className="text-lg font-semibold">
            Outras opções
          </h3>
          <div className="mt-3 space-y-4">
            {advice.alternatives.map((plan) => (
              <PlanCard key={plan.title + plan.costBrl} plan={plan} performance={advice.disclaimers.performance} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

function PlanCard({ plan, featured = false, performance }: { plan: UpgradePlan; featured?: boolean; performance: string }) {
  const [open, setOpen] = useState(featured);
  const [showAfter, setShowAfter] = useState(false);
  return (
    <div className={`mt-3 rounded-2xl border bg-surface ${featured ? "border-accent/40 shadow-sm" : "border-border"}`}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        className="flex w-full items-start gap-4 p-5 text-left sm:p-6"
      >
        <ChevronIcon className={`mt-1.5 size-4 shrink-0 text-subtle transition-transform ${open ? "rotate-90" : ""}`} />
        <span className="min-w-0 flex-1">
          <span className="block text-lg font-semibold text-pretty">{plan.title}</span>
          <span className="mt-1 block text-sm text-muted">{plan.impact}</span>
        </span>
        <span className="shrink-0 text-right">
          <span className="block text-xl font-semibold tabular-nums">{brl(plan.costBrl)}</span>
          <span className="block text-xs text-subtle">custo do upgrade</span>
        </span>
      </button>

      {open && (
        <div className="space-y-6 px-5 pb-6 sm:px-6 sm:pl-14">
          <div>
            <p className="text-sm font-semibold">O que comprar</p>
            <ul className="mt-2 divide-y divide-border rounded-xl border border-border">
              {plan.changes.map((change) => (
                <li key={change.part.id} className="flex flex-col gap-2 p-4 sm:flex-row sm:items-start">
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-2 text-xs font-medium text-muted uppercase">
                      {change.categoryLabel}
                      <span
                        className={`rounded-full px-2 py-0.5 text-[11px] normal-case ${change.role === "MAIN" ? "bg-accent-soft text-accent" : "bg-warn-soft text-warn"}`}
                      >
                        {change.role === "MAIN" ? "Principal" : "Necessário junto"}
                      </span>
                    </p>
                    <p className="mt-1 font-medium text-pretty">{change.part.name}</p>
                    <p className="mt-0.5 text-sm text-muted">{change.reason}</p>
                  </div>
                  {change.price && (
                    <span className="shrink-0 text-right font-semibold tabular-nums">
                      {brl(change.price.amountBrl)}
                      {change.price.isExample && <span className="block text-xs font-normal text-subtle">fictício</span>}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>

          <div>
            <p className="text-sm font-semibold">O que muda junto (e o que não muda)</p>
            <ol className="mt-2 space-y-2">
              {plan.dependencies.map((text, index) => (
                <li key={text} className="flex gap-3 text-sm leading-relaxed">
                  <span className="grid size-5 shrink-0 place-items-center rounded-full bg-surface-muted font-mono text-[11px] text-muted">
                    {index + 1}
                  </span>
                  {text}
                </li>
              ))}
            </ol>
          </div>

          {plan.kept.length > 0 && (
            <div>
              <p className="text-sm font-semibold">Continuam no seu PC</p>
              <ul className="mt-2 flex flex-wrap gap-2">
                {plan.kept.map((part) => (
                  <li key={part.id} className="inline-flex items-center gap-1.5 rounded-full bg-ok-soft px-3 py-1 text-sm text-ok">
                    <CheckIcon className="size-3.5" strokeWidth={2.5} />
                    {part.categoryLabel}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <p className="text-xs text-subtle">{performance}</p>

          <div>
            <Button variant="secondary" onClick={() => setShowAfter(!showAfter)} aria-expanded={showAfter}>
              {showAfter ? "Esconder o PC completo" : "Ver o PC completo depois do upgrade"}
            </Button>
          </div>
          {showAfter && (
            <div className="rounded-2xl border border-border bg-background p-4 sm:p-6">
              <BuildView build={plan.after} title="Seu PC depois do upgrade" subtitle="Peças que você já tem não entram no total." />
            </div>
          )}
        </div>
      )}
    </div>
  );
}
