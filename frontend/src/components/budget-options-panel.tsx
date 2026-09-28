"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { brl, brlShort } from "@/lib/format";
import { needsToParams } from "@/lib/needs";
import type { BudgetOption, Needs } from "@/lib/types";
import { ArrowIcon, InfoIcon } from "./icons";

const MAX_CHANGES = 4;

function capitalize(text: string) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function OptionCard({ option, needs }: { option: BudgetOption; needs: Needs }) {
  const higher = option.budgetBrl > needs.budgetBrl;
  const difference = Math.abs(option.budgetBrl - needs.budgetBrl);
  return (
    <div className="flex flex-col rounded-2xl border border-border bg-surface p-5">
      <p className="text-xs font-medium tracking-wide text-muted uppercase">
        {higher ? `Com ${brlShort(difference)} a mais` : `Com ${brlShort(difference)} a menos`}
      </p>
      <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums">{brlShort(option.budgetBrl)}</p>

      {!option.feasible ? (
        <p className="mt-3 text-sm leading-relaxed text-muted">
          Não fecha uma configuração completa e compatível para o que você quer fazer.
        </p>
      ) : (
        <>
          <p className={`mt-3 font-medium leading-snug ${option.notWorthIt ? "text-muted" : ""}`}>
            {capitalize(option.performanceText ?? "")}
          </p>
          {option.notWorthIt && (
            <p className="mt-2 flex gap-2 rounded-xl bg-accent-soft/70 px-3 py-2 text-sm leading-relaxed">
              <InfoIcon className="mt-0.5 size-4 shrink-0 text-accent" />
              Gastar mais quase não muda o desempenho para o que você quer fazer.
            </p>
          )}
          {option.changes.length > 0 ? (
            <ul className="mt-3 space-y-1.5 text-sm">
              {option.changes.slice(0, MAX_CHANGES).map((change) => (
                <li key={change.category} className="leading-snug">
                  <span className="text-muted">{change.categoryLabel}: </span>
                  {change.to ?? <span className="text-muted">sem {change.categoryLabel.toLowerCase()}</span>}
                </li>
              ))}
              {option.changes.length > MAX_CHANGES && (
                <li className="text-muted">
                  e mais {option.changes.length - MAX_CHANGES} {option.changes.length - MAX_CHANGES === 1 ? "peça" : "peças"}
                </li>
              )}
            </ul>
          ) : (
            <p className="mt-3 text-sm text-muted">As mesmas peças.</p>
          )}
          <div className="mt-auto pt-4">
            {option.totalBrl != null && <p className="text-sm text-muted">Total estimado: {brl(option.totalBrl)}</p>}
            {option.changes.length > 0 && (
              <Link
                href={`/montar/resultado?${needsToParams({ ...needs, budgetBrl: option.budgetBrl })}`}
                className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-accent underline-offset-2 hover:underline"
              >
                Ver essa configuração
                <ArrowIcon className="size-3.5" />
              </Link>
            )}
          </div>
        </>
      )}
    </div>
  );
}

/** "What if I change the budget?": the same engine at a lower and a higher budget, compared with this build. */
export function BudgetOptionsPanel({ needs }: { needs: Needs }) {
  const [options, setOptions] = useState<BudgetOption[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.budgetOptions(needs).then(
      (result) => !cancelled && setOptions(result),
      () => !cancelled && setFailed(true),
    );
    return () => {
      cancelled = true;
    };
  }, [needs]);

  if (failed) return null;

  return (
    <section aria-labelledby="budget-title" className="rounded-2xl border border-border bg-surface-muted/40 p-5 sm:p-6">
      <h2 id="budget-title" className="text-lg font-semibold">
        E se eu mudar o orçamento?
      </h2>
      <p className="mt-1 text-sm text-muted">Montamos de novo com um valor menor e um maior para você comparar.</p>
      {options === null ? (
        <div className="mt-4 grid gap-4 sm:grid-cols-2" aria-busy="true" aria-label="Calculando alternativas">
          {[0, 1].map((key) => (
            <div key={key} className="h-44 animate-pulse rounded-2xl bg-border/60" />
          ))}
        </div>
      ) : (
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          {options.map((option) => (
            <OptionCard key={option.budgetBrl} option={option} needs={needs} />
          ))}
        </div>
      )}
    </section>
  );
}
