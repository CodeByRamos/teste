"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { brlShort } from "@/lib/format";
import { needsToParams } from "@/lib/needs";
import type { BudgetOption, Needs } from "@/lib/types";
import { ArrowIcon, InfoIcon, PiggyIcon, RocketIcon } from "./icons";
import { IsoPart } from "./pc-diagram";
import { Price } from "./ui";

const MAX_CHANGES = 4;

function capitalize(text: string) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** One alternative budget, laid out like the spending planner: figures, a usage bar, the parts that change, the total. */
function OptionCard({ option, needs }: { option: BudgetOption; needs: Needs }) {
  const higher = option.budgetBrl > needs.budgetBrl;
  const difference = Math.abs(option.budgetBrl - needs.budgetBrl);
  const used = option.totalBrl != null ? Math.min(100, (option.totalBrl / option.budgetBrl) * 100) : null;
  return (
    <div className="inner-card flex flex-col rounded-2xl p-5">
      <div className="flex items-center gap-3">
        <span aria-hidden className="shrink-0 text-accent drop-shadow-[0_0_8px_rgb(168_85_247/0.5)]">
          {higher ? <RocketIcon className="size-9" strokeWidth={1.6} /> : <PiggyIcon className="size-9" strokeWidth={1.6} />}
        </span>
        <div>
          <p className="text-xs font-semibold tracking-wide text-accent uppercase">
            {higher ? `Com ${brlShort(difference)} a mais` : `Com ${brlShort(difference)} a menos`}
          </p>
          <p className="text-sm text-muted">{higher ? "Mais desempenho" : "Mais economia"}</p>
        </div>
      </div>

      <dl className="mt-3 grid grid-cols-2 gap-3 text-center">
        <div className="rounded-lg border border-border px-2 py-3">
          <dt className="text-[11px] font-semibold tracking-wide text-accent uppercase">Orçamento</dt>
          <dd className="mt-1 text-base font-semibold whitespace-nowrap tabular-nums sm:text-lg">
            <Price amount={option.budgetBrl} />
          </dd>
        </div>
        <div className="rounded-lg border border-border px-2 py-3">
          <dt className="text-[11px] font-semibold tracking-wide text-accent uppercase">Total</dt>
          <dd className="mt-1 text-base font-semibold whitespace-nowrap tabular-nums sm:text-lg">
            {option.totalBrl != null ? <Price amount={option.totalBrl} /> : "—"}
          </dd>
        </div>
      </dl>

      {used != null && (
        <div className="mt-3">
          <div className="h-2 overflow-hidden rounded-full bg-surface-muted" aria-hidden>
            <div className="h-full rounded-full bg-primary" style={{ width: `${used}%` }} />
          </div>
          <p className="mt-1.5 text-xs text-subtle">{Math.round(used)}% do orçamento usado</p>
        </div>
      )}

      {!option.feasible ? (
        <p className="mt-4 text-sm leading-relaxed text-muted">Não fecha uma configuração completa e compatível para o que você quer fazer.</p>
      ) : (
        <>
          <p className="mt-4 font-medium leading-snug">{capitalize(option.performanceText ?? "")}</p>

          {option.changes.length > 0 ? (
            <>
              <p className="mt-4 text-xs font-semibold tracking-wide text-accent uppercase">O que muda</p>
              <ul className="mt-1">
                {option.changes.slice(0, MAX_CHANGES).map((change) => (
                  <li key={change.category} className="flex items-center gap-3 border-b border-border py-2.5">
                    <IsoPart category={change.category} className="size-9 shrink-0" />
                    <span className="min-w-0 flex-1">
                      <span className="block text-xs font-semibold tracking-wide text-accent uppercase">{change.categoryLabel}</span>
                      <span className="mt-0.5 block truncate text-sm">
                        {change.to ?? <span className="text-muted">sem {change.categoryLabel.toLowerCase()}</span>}
                      </span>
                      {change.from && <span className="block truncate text-xs text-subtle">no lugar de {change.from}</span>}
                    </span>
                  </li>
                ))}
              </ul>
              {option.changes.length > MAX_CHANGES && (
                <p className="mt-2 text-sm text-muted">
                  e mais {option.changes.length - MAX_CHANGES} {option.changes.length - MAX_CHANGES === 1 ? "peça" : "peças"}
                </p>
              )}
            </>
          ) : (
            <p className="mt-4 text-sm text-muted">As mesmas peças.</p>
          )}

          <div className="mt-auto pt-4">
            {option.notWorthIt && (
              <p className="mb-3 flex gap-2 rounded-lg border border-accent/30 px-3 py-2 text-sm leading-relaxed">
                <InfoIcon className="mt-0.5 size-4 shrink-0 text-accent" />
                Gastar mais quase não muda o desempenho para o que você quer fazer.
              </p>
            )}
            {option.changes.length > 0 && (
              <Link
                href={`/montar/resultado?${needsToParams({ ...needs, budgetBrl: option.budgetBrl })}`}
                className="inline-flex items-center gap-1.5 text-sm font-medium text-accent underline-offset-2 hover:underline"
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
    <section aria-labelledby="budget-title" className="panel rounded-2xl p-5 sm:p-6">
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
