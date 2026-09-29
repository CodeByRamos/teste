"use client";

import { useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { brl, brlShort } from "@/lib/format";
import { needsToParams } from "@/lib/needs";
import type { Needs, Options, Resolution, SearchResult, UseCase } from "@/lib/types";
import { ArrowIcon, CheckIcon, XIcon } from "./icons";
import { CATEGORY_LABELS, PartPicker } from "./part-picker";
import { Button } from "./ui";

const BUDGET_PRESETS = [3000, 5000, 8000, 12000];
const GAMING: UseCase[] = ["GAMING_COMPETITIVE", "GAMING_AAA"];

type Step = "uses" | "resolution" | "budget" | "owned";

const STEP_NAMES: Record<Step, string> = {
  uses: "Usos",
  resolution: "Resolução",
  budget: "Orçamento",
  owned: "Suas peças",
};

/** What each resolution means in practice, shown under its name. */
const RESOLUTION_HINTS: Record<string, string> = {
  FULL_HD: "1920 × 1080. A mais comum, pede menos da placa de vídeo.",
  QHD: "2560 × 1440. Mais nitidez, pede uma placa intermediária.",
  UHD_4K: "3840 × 2160. Máxima nitidez, pede uma placa forte.",
};

/** Selectable card used for uses, resolutions and yes/no answers. */
function OptionCard({
  wide = false,
  selected,
  onSelect,
  type,
  name,
  title,
  description,
}: {
  /** Spans both columns (keeps an odd last card from sitting alone). */
  wide?: boolean;
  selected: boolean;
  onSelect: () => void;
  type: "checkbox" | "radio";
  name?: string;
  title: string;
  description?: ReactNode;
}) {
  return (
    <label
      className={`group flex h-full cursor-pointer items-start gap-3 rounded-xl border p-4 ${wide ? "sm:col-span-2" : ""} transition-[border-color,background-color,translate] duration-200 active:scale-[0.99] has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-accent ${
        selected ? "border-accent bg-accent-soft/50" : "border-border bg-surface hover:-translate-y-0.5 hover:border-accent/50"
      }`}
    >
      <input type={type} name={name} checked={selected} onChange={onSelect} className="sr-only" />
      <span
        aria-hidden
        className={`mt-0.5 grid size-5 shrink-0 place-items-center border transition-colors ${type === "radio" ? "rounded-full" : "rounded-md"} ${
          selected ? "border-accent bg-primary text-accent-foreground" : "border-border-strong group-hover:border-accent/60"
        }`}
      >
        {selected && (type === "radio" ? <span className="size-2 rounded-full bg-accent-foreground" /> : <CheckIcon className="size-3.5" strokeWidth={3} />)}
      </span>
      <span className="min-w-0">
        <span className={`block font-medium transition-colors ${selected ? "text-foreground" : ""}`}>{title}</span>
        {description && <span className="mt-0.5 block text-sm leading-relaxed text-muted">{description}</span>}
      </span>
    </label>
  );
}

/** One line of the live "Seu pedido" summary. */
function SummaryRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="border-b border-border py-3 last:border-b-0">
      <dt className="text-xs font-semibold tracking-wide text-accent uppercase">{label}</dt>
      <dd className="mt-1 text-sm">{children}</dd>
    </div>
  );
}

export function BuildWizard({ options, initial }: { options: Options; initial: Partial<Needs> }) {
  const router = useRouter();
  const [uses, setUses] = useState<UseCase[]>(initial.useCases ?? []);
  const [primary, setPrimary] = useState<UseCase | null>(initial.primaryUse ?? null);
  const [resolution, setResolution] = useState<Resolution | null>(initial.resolution ?? null);
  const [budget, setBudget] = useState<number>(initial.budgetBrl ?? 5000);
  const [owned, setOwned] = useState<SearchResult[]>([]);
  const [hasParts, setHasParts] = useState<boolean | null>(null);
  const [planUpgrades, setPlanUpgrades] = useState<boolean>(initial.planUpgrades ?? false);

  const playsGames = uses.some((use) => GAMING.includes(use));
  const steps: Step[] = playsGames ? ["uses", "resolution", "budget", "owned"] : ["uses", "budget", "owned"];
  const [stepIndex, setStepIndex] = useState(initial.useCases?.length ? Math.min(steps.indexOf("budget"), steps.length - 1) : 0);
  const current = Math.min(stepIndex, steps.length - 1);
  const step = steps[current];

  const budgetValid = budget >= options.minBudgetBrl && budget <= options.maxBudgetBrl;
  const canContinue = step === "uses" ? uses.length > 0 : step === "budget" ? budgetValid : step === "owned" ? hasParts !== null : true;
  const labelOfUse = (use: UseCase) => options.useCases.find((u) => u.value === use)?.label ?? use;

  function toggleUse(use: UseCase) {
    setUses((list) => {
      const next = list.includes(use) ? list.filter((u) => u !== use) : [...list, use];
      if (primary && !next.includes(primary)) setPrimary(null);
      return next;
    });
  }

  function finish() {
    const needs: Needs = {
      budgetBrl: budget,
      useCases: uses,
      primaryUse: uses.length > 1 ? primary : null,
      resolution: playsGames ? resolution : null,
      ownedComponentIds: owned.map((part) => part.id),
      planUpgrades,
    };
    router.push(`/montar/resultado?${needsToParams(needs)}`);
  }

  function goTo(index: number) {
    setStepIndex(index);
    window.scrollTo({ top: 0 });
  }

  function next() {
    if (current >= steps.length - 1) finish();
    else goTo(current + 1);
  }

  return (
    <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_300px] lg:gap-12">
      <div className="min-w-0">
        {/* Named steps: completed ones can be revisited, later ones stay locked until reached. */}
        <nav aria-label="Etapas">
          <ol className="grid gap-2" style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }}>
            {steps.map((s, index) => {
              const done = index < current;
              const active = index === current;
              return (
                <li key={s}>
                  <button
                    type="button"
                    onClick={() => done && goTo(index)}
                    disabled={!done}
                    aria-current={active ? "step" : undefined}
                    className={`group w-full text-left ${done ? "cursor-pointer" : "cursor-default"}`}
                  >
                    <span className={`block h-1 rounded-full transition-colors duration-300 ${index <= current ? "bg-primary" : "bg-border"} ${done ? "group-hover:bg-accent" : ""}`} />
                    <span
                      className={`mt-2 block truncate text-xs font-medium transition-colors ${active ? "text-accent" : done ? "text-muted group-hover:text-foreground" : "text-subtle"}`}
                    >
                      {STEP_NAMES[s]}
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>
        </nav>

        <div key={step} className="mt-10 animate-[panel-in_0.3s_ease-out]">
          {step === "uses" && (
            <fieldset>
              <legend className="text-3xl font-bold tracking-tight text-balance">O que você quer fazer com esse computador?</legend>
              <p className="mt-2 text-muted">Escolha tudo o que se aplica.</p>
              <div className="mt-6 grid gap-3 sm:grid-cols-2">
                {options.useCases.map((use, index) => (
                  <OptionCard
                    key={use.value}
                    wide={options.useCases.length % 2 === 1 && index === options.useCases.length - 1}
                    type="checkbox"
                    selected={uses.includes(use.value)}
                    onSelect={() => toggleUse(use.value)}
                    title={use.label}
                    description={use.description}
                  />
                ))}
              </div>
              {uses.length > 1 && (
                <div className="mt-8">
                  <p className="font-medium">Qual é o mais importante para você?</p>
                  <p className="text-sm text-muted">Se o orçamento apertar, priorizamos esse uso. Opcional.</p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {uses.map((use) => (
                      <button
                        key={use}
                        type="button"
                        aria-pressed={primary === use}
                        onClick={() => setPrimary(primary === use ? null : use)}
                        className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors active:scale-[0.97] ${primary === use ? "border-accent bg-primary text-accent-foreground" : "border-border-strong hover:border-accent/60"}`}
                      >
                        {labelOfUse(use)}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </fieldset>
          )}

          {step === "resolution" && (
            <fieldset>
              <legend className="text-3xl font-bold tracking-tight text-balance">Em que resolução você joga?</legend>
              <p className="mt-2 max-w-[60ch] text-muted">Depende do seu monitor. Quanto maior a resolução, mais nítida a imagem e mais exigida a placa de vídeo.</p>
              <div className="mt-6 grid gap-3 sm:grid-cols-2">
                {[...options.resolutions, { value: null, label: "Não sei" }].map((option) => (
                  <OptionCard
                    key={option.label}
                    type="radio"
                    name="resolution"
                    selected={resolution === option.value}
                    onSelect={() => setResolution(option.value as Resolution | null)}
                    title={option.label}
                    description={option.value ? RESOLUTION_HINTS[option.value] : "Sem problema: consideramos Full HD, a mais comum."}
                  />
                ))}
              </div>
            </fieldset>
          )}

          {step === "budget" && (
            <div>
              <h2 className="text-3xl font-bold tracking-tight text-balance">Quanto você pretende investir?</h2>
              <p className="mt-2 text-muted">Valor total das peças. Montamos a melhor configuração que couber.</p>
              <div className="panel mt-6 rounded-2xl p-5 sm:p-6">
                <label htmlFor="budget" className="text-xs font-semibold tracking-wide text-accent uppercase">
                  Orçamento
                </label>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-3xl font-semibold text-accent">R$</span>
                  <input
                    id="budget"
                    type="number"
                    inputMode="numeric"
                    min={options.minBudgetBrl}
                    max={options.maxBudgetBrl}
                    step={100}
                    value={budget}
                    onChange={(event) => setBudget(Number(event.target.value))}
                    aria-describedby="budget-range budget-error"
                    aria-invalid={!budgetValid}
                    className="w-full bg-transparent font-display text-5xl font-bold tracking-tight tabular-nums focus:outline-none"
                  />
                </div>
                <input
                  type="range"
                  min={options.minBudgetBrl}
                  max={20000}
                  step={100}
                  value={Math.min(budget, 20000)}
                  onChange={(event) => setBudget(Number(event.target.value))}
                  aria-label="Ajustar orçamento"
                  className="mt-5 w-full accent-[var(--accent)]"
                />
                <p id="budget-range" className="mt-1 flex justify-between text-xs text-subtle">
                  <span>{brlShort(options.minBudgetBrl)}</span>
                  <span>{brlShort(20000)}+</span>
                </p>
                <div className="mt-5 flex flex-wrap gap-2">
                  {BUDGET_PRESETS.map((preset) => (
                    <button
                      key={preset}
                      type="button"
                      onClick={() => setBudget(preset)}
                      className={`rounded-full border px-3.5 py-1.5 text-sm tabular-nums transition-colors active:scale-[0.97] ${budget === preset ? "border-accent bg-primary font-semibold text-accent-foreground" : "border-border-strong hover:border-accent/60"}`}
                    >
                      {brlShort(preset)}
                    </button>
                  ))}
                </div>
                {!budgetValid && (
                  <p id="budget-error" className="mt-3 text-sm text-bad">
                    Informe um valor entre {brlShort(options.minBudgetBrl)} e {brlShort(options.maxBudgetBrl)}.
                  </p>
                )}
              </div>
              <div className="mt-4">
                <OptionCard
                  type="checkbox"
                  selected={planUpgrades}
                  onSelect={() => setPlanUpgrades(!planUpgrades)}
                  title="Quero poder melhorar depois"
                  description="Preferimos uma plataforma que ainda recebe processadores novos e uma fonte com folga para uma placa de vídeo mais forte. Pode sobrar um pouco menos para desempenho hoje."
                />
              </div>
            </div>
          )}

          {step === "owned" && (
            <div>
              <h2 className="text-3xl font-bold tracking-tight text-balance">Você já tem alguma peça para aproveitar?</h2>
              <p className="mt-2 text-muted">Peças que você já tem não entram no custo, e montamos o resto em volta delas.</p>
              <div className="mt-6 grid gap-3 sm:grid-cols-2">
                <OptionCard
                  type="radio"
                  name="has-parts"
                  selected={hasParts === false}
                  onSelect={() => {
                    setHasParts(false);
                    setOwned([]);
                  }}
                  title="Não, vou comprar tudo"
                  description="Montamos o computador completo."
                />
                <OptionCard
                  type="radio"
                  name="has-parts"
                  selected={hasParts === true}
                  onSelect={() => setHasParts(true)}
                  title="Sim, tenho algumas peças"
                  description="Você escolhe as peças e montamos o resto."
                />
              </div>
              {hasParts && (
                <div className="mt-6 space-y-4">
                  {owned.length > 0 && (
                    <ul className="panel divide-y divide-border rounded-2xl">
                      {owned.map((part) => (
                        <li key={part.id} className="flex items-center gap-3 px-4 py-3">
                          <span className="min-w-0 flex-1">
                            <span className="block text-xs font-semibold tracking-wide text-accent uppercase">{CATEGORY_LABELS[part.category]}</span>
                            <span className="block truncate text-sm font-medium">{part.name}</span>
                          </span>
                          <button
                            type="button"
                            onClick={() => setOwned(owned.filter((p) => p.id !== part.id))}
                            className="rounded-lg p-1.5 text-muted hover:bg-surface-muted hover:text-foreground"
                            aria-label={`Remover ${part.name}`}
                          >
                            <XIcon className="size-4" />
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                  <PartPicker
                    excludeIds={owned.map((part) => part.id)}
                    onPick={(part) =>
                      setOwned((list) => [...list.filter((p) => p.category !== part.category || part.category === "STORAGE"), part])
                    }
                  />
                </div>
              )}
            </div>
          )}
        </div>

        <div className="mt-10 flex items-center justify-between gap-3 border-t border-border pt-6">
          <Button variant="ghost" onClick={() => goTo(Math.max(0, current - 1))} disabled={current === 0} className="active:scale-[0.98]">
            Voltar
          </Button>
          <Button onClick={next} disabled={!canContinue} className="active:scale-[0.98]">
            {step === "owned" ? "Montar meu PC" : "Continuar"}
            <ArrowIcon className="size-4" />
          </Button>
        </div>
      </div>

      {/* Live summary of the answers so far. */}
      <aside className="hidden lg:block">
        <div className="panel sticky top-24 rounded-2xl p-5">
          <h2 className="text-sm font-semibold">Seu pedido</h2>
          <dl className="mt-2">
            <SummaryRow label="Usos">
              {uses.length ? (
                <span className="flex flex-wrap gap-1.5">
                  {uses.map((use) => (
                    <span key={use} className="rounded-md border border-border-strong px-1.5 py-0.5 text-xs text-muted">
                      {labelOfUse(use)}
                      {primary === use && <span className="text-accent"> · prioridade</span>}
                    </span>
                  ))}
                </span>
              ) : (
                <span className="text-subtle">Nenhum ainda</span>
              )}
            </SummaryRow>
            {playsGames && (
              <SummaryRow label="Resolução">
                {options.resolutions.find((r) => r.value === resolution)?.label ?? <span className="text-subtle">Full HD (padrão)</span>}
              </SummaryRow>
            )}
            <SummaryRow label="Orçamento">
              <span className="font-display text-lg font-bold tabular-nums">
                <span className="text-accent">R$</span> {brl(budget).replace(/^R\$\s*/, "")}
              </span>
              {planUpgrades && <span className="mt-0.5 block text-xs text-muted">Pensando em melhorar depois</span>}
            </SummaryRow>
            <SummaryRow label="Peças que você tem">
              {owned.length ? `${owned.length} ${owned.length === 1 ? "peça" : "peças"}` : <span className="text-subtle">{hasParts === false ? "Nenhuma" : "A definir"}</span>}
            </SummaryRow>
          </dl>
        </div>
      </aside>
    </div>
  );
}
