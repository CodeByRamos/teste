"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { brlShort } from "@/lib/format";
import { needsToParams } from "@/lib/needs";
import type { Needs, Options, Resolution, SearchResult, UseCase } from "@/lib/types";
import { ArrowIcon, CheckIcon, XIcon } from "./icons";
import { CATEGORY_LABELS, PartPicker } from "./part-picker";
import { Button } from "./ui";

const BUDGET_PRESETS = [3000, 5000, 8000, 12000];
const GAMING: UseCase[] = ["GAMING_COMPETITIVE", "GAMING_AAA"];

type Step = "uses" | "resolution" | "budget" | "owned";

export function BuildWizard({ options, initial }: { options: Options; initial: Partial<Needs> }) {
  const router = useRouter();
  const [uses, setUses] = useState<UseCase[]>(initial.useCases ?? []);
  const [primary, setPrimary] = useState<UseCase | null>(initial.primaryUse ?? null);
  const [resolution, setResolution] = useState<Resolution | null>(initial.resolution ?? null);
  const [budget, setBudget] = useState<number>(initial.budgetBrl ?? 5000);
  const [owned, setOwned] = useState<SearchResult[]>([]);
  const [hasParts, setHasParts] = useState<boolean | null>(null);

  const playsGames = uses.some((use) => GAMING.includes(use));
  const steps: Step[] = playsGames ? ["uses", "resolution", "budget", "owned"] : ["uses", "budget", "owned"];
  const [stepIndex, setStepIndex] = useState(initial.useCases?.length ? Math.min(steps.indexOf("budget"), steps.length - 1) : 0);
  const step = steps[Math.min(stepIndex, steps.length - 1)];

  const budgetValid = budget >= options.minBudgetBrl && budget <= options.maxBudgetBrl;
  const canContinue = step === "uses" ? uses.length > 0 : step === "budget" ? budgetValid : true;

  function toggleUse(use: UseCase) {
    setUses((current) => {
      const next = current.includes(use) ? current.filter((u) => u !== use) : [...current, use];
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
    };
    router.push(`/montar/resultado?${needsToParams(needs)}`);
  }

  function goTo(index: number) {
    setStepIndex(index);
    window.scrollTo({ top: 0 });
  }

  function next() {
    if (stepIndex >= steps.length - 1) finish();
    else goTo(stepIndex + 1);
  }

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-8 flex items-center gap-3" aria-hidden>
        {steps.map((s, index) => (
          <span
            key={s}
            className={`h-1.5 flex-1 rounded-full transition-colors ${index <= stepIndex ? "bg-accent" : "bg-border"}`}
          />
        ))}
      </div>
      <p className="mb-2 text-sm text-muted">
        Etapa {stepIndex + 1} de {steps.length}
      </p>

      {step === "uses" && (
        <fieldset>
          <legend className="text-2xl font-semibold tracking-tight">O que você quer fazer com esse computador?</legend>
          <p className="mt-2 text-muted">Escolha tudo o que se aplica.</p>
          <div className="mt-6 divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface">
            {options.useCases.map((use) => {
              const checked = uses.includes(use.value);
              return (
                <label
                  key={use.value}
                  className={`flex cursor-pointer items-start gap-4 px-5 py-4 transition-colors ${checked ? "bg-accent-soft/60" : "hover:bg-surface-muted"}`}
                >
                  <input type="checkbox" checked={checked} onChange={() => toggleUse(use.value)} className="sr-only" />
                  <span
                    aria-hidden
                    className={`mt-0.5 grid size-5 shrink-0 place-items-center rounded-md border transition-colors ${checked ? "border-accent bg-accent text-accent-foreground" : "border-border-strong bg-surface"}`}
                  >
                    {checked && <CheckIcon className="size-3.5" strokeWidth={3} />}
                  </span>
                  <span>
                    <span className="block font-medium">{use.label}</span>
                    <span className="block text-sm text-muted">{use.description}</span>
                  </span>
                </label>
              );
            })}
          </div>
          {uses.length > 1 && (
            <div className="mt-6">
              <p className="font-medium">Qual é o mais importante para você?</p>
              <p className="text-sm text-muted">Se o orçamento apertar, priorizamos esse uso. Opcional.</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {uses.map((use) => (
                  <button
                    key={use}
                    type="button"
                    aria-pressed={primary === use}
                    onClick={() => setPrimary(primary === use ? null : use)}
                    className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors ${primary === use ? "border-accent bg-accent text-accent-foreground" : "border-border-strong bg-surface hover:bg-surface-muted"}`}
                  >
                    {options.useCases.find((u) => u.value === use)?.label}
                  </button>
                ))}
              </div>
            </div>
          )}
        </fieldset>
      )}

      {step === "resolution" && (
        <fieldset>
          <legend className="text-2xl font-semibold tracking-tight">Em que resolução você joga?</legend>
          <p className="mt-2 text-muted">
            Depende do seu monitor. Resoluções maiores deixam a imagem mais nítida, mas exigem mais da placa de vídeo.
          </p>
          <div className="mt-6 divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface">
            {[...options.resolutions, { value: null, label: "Não sei" }].map((option) => {
              const selected = resolution === option.value;
              return (
                <label
                  key={option.label}
                  className={`flex cursor-pointer items-center gap-4 px-5 py-4 transition-colors ${selected ? "bg-accent-soft/60" : "hover:bg-surface-muted"}`}
                >
                  <input
                    type="radio"
                    name="resolution"
                    checked={selected}
                    onChange={() => setResolution(option.value as Resolution | null)}
                    className="size-4 accent-[var(--accent)]"
                  />
                  <span>
                    <span className="block font-medium">{option.label}</span>
                    {option.value === null && (
                      <span className="block text-sm text-muted">Sem problema: consideramos Full HD, a mais comum.</span>
                    )}
                  </span>
                </label>
              );
            })}
          </div>
        </fieldset>
      )}

      {step === "budget" && (
        <div>
          <h2 className="text-2xl font-semibold tracking-tight">Quanto você pretende investir?</h2>
          <p className="mt-2 text-muted">Valor total das peças. Montamos a melhor configuração que couber.</p>
          <div className="mt-6 rounded-2xl border border-border bg-surface p-5">
            <label htmlFor="budget" className="text-sm text-muted">
              Orçamento
            </label>
            <div className="mt-1 flex items-baseline gap-2">
              <span className="text-2xl font-semibold text-muted">R$</span>
              <input
                id="budget"
                type="number"
                inputMode="numeric"
                min={options.minBudgetBrl}
                max={options.maxBudgetBrl}
                step={100}
                value={budget}
                onChange={(event) => setBudget(Number(event.target.value))}
                className="w-full bg-transparent text-4xl font-semibold tracking-tight focus:outline-none"
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
              className="mt-4 w-full accent-[var(--accent)]"
            />
            <div className="mt-4 flex flex-wrap gap-2">
              {BUDGET_PRESETS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  onClick={() => setBudget(preset)}
                  className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors ${budget === preset ? "border-accent bg-accent-soft text-accent" : "border-border-strong hover:bg-surface-muted"}`}
                >
                  {brlShort(preset)}
                </button>
              ))}
            </div>
            {!budgetValid && (
              <p className="mt-3 text-sm text-bad">
                Informe um valor entre {brlShort(options.minBudgetBrl)} e {brlShort(options.maxBudgetBrl)}.
              </p>
            )}
          </div>
        </div>
      )}

      {step === "owned" && (
        <div>
          <h2 className="text-2xl font-semibold tracking-tight">Você já tem alguma peça para aproveitar?</h2>
          <p className="mt-2 text-muted">Peças que você já tem não entram no custo, e montamos o resto em volta delas.</p>
          <div className="mt-6 flex flex-wrap gap-2">
            <button
              type="button"
              aria-pressed={hasParts === false}
              onClick={() => {
                setHasParts(false);
                setOwned([]);
              }}
              className={`rounded-xl border px-4 py-2.5 text-sm font-medium transition-colors ${hasParts === false ? "border-accent bg-accent-soft text-accent" : "border-border-strong bg-surface hover:bg-surface-muted"}`}
            >
              Não, vou comprar tudo
            </button>
            <button
              type="button"
              aria-pressed={hasParts === true}
              onClick={() => setHasParts(true)}
              className={`rounded-xl border px-4 py-2.5 text-sm font-medium transition-colors ${hasParts === true ? "border-accent bg-accent-soft text-accent" : "border-border-strong bg-surface hover:bg-surface-muted"}`}
            >
              Sim, tenho algumas peças
            </button>
          </div>
          {hasParts && (
            <div className="mt-6 space-y-4">
              {owned.length > 0 && (
                <ul className="divide-y divide-border rounded-2xl border border-border bg-surface">
                  {owned.map((part) => (
                    <li key={part.id} className="flex items-center gap-3 px-4 py-3">
                      <span className="min-w-0 flex-1">
                        <span className="block text-xs text-muted">{CATEGORY_LABELS[part.category]}</span>
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
                  setOwned((current) => [
                    ...current.filter((p) => p.category !== part.category || part.category === "STORAGE"),
                    part,
                  ])
                }
              />
            </div>
          )}
        </div>
      )}

      <div className="mt-10 flex items-center justify-between gap-3">
        <Button variant="ghost" onClick={() => goTo(Math.max(0, stepIndex - 1))} disabled={stepIndex === 0}>
          Voltar
        </Button>
        <Button onClick={next} disabled={!canContinue || (step === "owned" && hasParts === null)}>
          {step === "owned" ? "Montar meu PC" : "Continuar"}
          <ArrowIcon className="size-4" />
        </Button>
      </div>
    </div>
  );
}
