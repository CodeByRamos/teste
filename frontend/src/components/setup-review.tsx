import type { BuildItem, BuildView } from "@/lib/types";
import { AlertIcon, CheckIcon, InfoIcon } from "./icons";
import { IsoPart } from "./pc-diagram";
import { Price } from "./ui";

function cost(item: BuildItem) {
  return item.owned ? 0 : (item.price?.amountBrl ?? 0);
}

/**
 * "Sobre o PC": a short review of the setup, written only from what the engines already decided (totals,
 * compatibility checks, the future outlook, notes). Nothing here is invented: every line points back to a result.
 */
export function SetupReview({ build }: { build: BuildView }) {
  const { totals, compatibility, future, notes } = build;
  const findings = compatibility.findings;
  const passed = findings.filter((finding) => finding.status === "OK").length;
  const concerns = findings.filter((finding) => finding.status !== "OK" && finding.ruleId !== "essential.missing");
  const psu = Number.parseInt(
    build.items.find((item) => item.category === "POWER_SUPPLY")?.specs.find((spec) => spec.label === "Potência")?.value ?? "",
    10,
  );
  const headroom = Number.isFinite(psu) && psu > 0 ? psu - compatibility.power.estimatedLoadWatts : null;
  const invested = [...build.items].filter((item) => cost(item) > 0).sort((a, b) => cost(b) - cost(a)).slice(0, 2);
  const strengths = future?.aspects.filter((aspect) => aspect.level === "GOOD") ?? [];
  const limits = future?.aspects.filter((aspect) => aspect.level !== "GOOD") ?? [];
  const observations = [...(totals.allPriced ? [] : ["Algumas peças ainda não têm preço disponível e não entram no total."]), ...notes];

  return (
    <div className="panel space-y-8 rounded-2xl p-5 sm:p-6">
      {/* The setup in four numbers. */}
      <dl className="grid grid-cols-2 gap-x-8 gap-y-5 sm:grid-cols-4">
        <Figure label="Total">
          <Price amount={totals.totalBrl} />
        </Figure>
        {totals.budgetBrl != null && (
          <Figure label="Do orçamento">{Math.round((totals.totalBrl / totals.budgetBrl) * 100)}%</Figure>
        )}
        <Figure label="Verificações">
          {passed} de {findings.length}
        </Figure>
        <Figure label="Folga da fonte">{headroom != null ? `${headroom} W` : "-"}</Figure>
      </dl>

      {invested.length > 0 && (
        <section aria-labelledby="review-investimento">
          <h3 id="review-investimento" className="font-semibold">
            Onde está o investimento
          </h3>
          <ul className="mt-3 space-y-4">
            {invested.map((item) => (
              <li key={item.category} className="flex gap-4">
                <IsoPart category={item.category} className="size-12 shrink-0" />
                <div className="min-w-0">
                  <p className="text-sm text-muted">
                    {item.categoryLabel} · {Math.round((cost(item) / totals.totalBrl) * 100)}% do total
                  </p>
                  <p className="font-medium">{item.component.name}</p>
                  <p className="mt-1 text-sm leading-relaxed text-muted">{item.explanation.reason}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="grid gap-8 md:grid-cols-2">
        <section aria-labelledby="review-fortes">
          <h3 id="review-fortes" className="font-semibold">
            Pontos fortes
          </h3>
          <ul className="mt-3 space-y-3 text-sm leading-relaxed">
            {concerns.length === 0 && (
              <Point tone="ok">Todas as {findings.length} verificações de compatibilidade passaram.</Point>
            )}
            {headroom != null && headroom > 0 && (
              <Point tone="ok">
                A fonte tem {headroom} W de folga sobre o consumo estimado de {compatibility.power.estimatedLoadWatts} W.
              </Point>
            )}
            {strengths.map((aspect) => (
              <Point key={aspect.id} tone="ok">
                {aspect.headline}
              </Point>
            ))}
          </ul>
        </section>

        <section aria-labelledby="review-atencao">
          <h3 id="review-atencao" className="font-semibold">
            Pontos de atenção
          </h3>
          <ul className="mt-3 space-y-3 text-sm leading-relaxed">
            {concerns.map((finding) => (
              <Point key={finding.ruleId + finding.title} tone="warn">
                {finding.title}: {finding.explanation}
              </Point>
            ))}
            {limits.map((aspect) => (
              <Point key={aspect.id} tone="warn">
                {aspect.headline}
              </Point>
            ))}
            {concerns.length === 0 && limits.length === 0 && <li className="text-muted">Nada que precise de atenção agora.</li>}
          </ul>
        </section>
      </div>

      {observations.length > 0 && (
        <section aria-labelledby="review-obs" className="border-t border-border pt-6">
          <h3 id="review-obs" className="flex items-center gap-2 font-semibold">
            <InfoIcon className="size-4 text-accent" /> Observações
          </h3>
          <ul className="mt-3 space-y-2 text-sm leading-relaxed text-muted">
            {observations.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

function Figure({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="mt-0.5 font-display text-2xl font-bold tracking-tight tabular-nums">{children}</dd>
    </div>
  );
}

function Point({ tone, children }: { tone: "ok" | "warn"; children: React.ReactNode }) {
  const Icon = tone === "ok" ? CheckIcon : AlertIcon;
  return (
    <li className="flex gap-2.5">
      <Icon className={`mt-0.5 size-4 shrink-0 ${tone === "ok" ? "text-ok" : "text-warn"}`} strokeWidth={2.5} aria-hidden />
      <span>{children}</span>
    </li>
  );
}
