"use client";

import { useState } from "react";
import type { BuildView, Finding, Status } from "@/lib/types";
import { AlertIcon, CheckIcon, ChevronIcon, XIcon } from "./icons";
import { Collapse } from "./ui";

const STATUS_ICON: Record<Status, { Icon: typeof CheckIcon; color: string; border: string; label: string }> = {
  OK: { Icon: CheckIcon, color: "text-ok", border: "", label: "Compatível" },
  WARNING: { Icon: AlertIcon, color: "text-warn", border: "border-warn/40", label: "Atenção" },
  INCOMPATIBLE: { Icon: XIcon, color: "text-bad", border: "border-bad/40", label: "Incompatível" },
};

/** One compatibility check as a card: the bare status icon beside its title, the explanation and the numbers. */
function FindingCard({ finding }: { finding: Finding }) {
  const { Icon, color, border, label } = STATUS_ICON[finding.status];
  return (
    <li className={`inner-card flex h-full flex-col rounded-xl p-4 ${border}`}>
      <div className="flex items-start gap-2.5">
        <Icon className={`mt-0.5 size-4.5 shrink-0 ${color}`} strokeWidth={2.5} role="img" aria-label={label} />
        <p className="font-medium leading-snug">{finding.title}</p>
      </div>
      <p className="mt-2 text-sm leading-relaxed text-muted">{finding.explanation}</p>
      {finding.technicalDetail && (
        <p className="mt-auto pt-3 font-mono text-xs leading-relaxed text-subtle">{finding.technicalDetail}</p>
      )}
      {!finding.verified && <p className="mt-2 text-xs text-subtle">Não foi possível confirmar com os dados disponíveis.</p>}
    </li>
  );
}

function FindingGrid({ findings }: { findings: Finding[] }) {
  return (
    <ul className="mt-3 grid gap-3 sm:grid-cols-2 2xl:grid-cols-3">
      {findings.map((f) => (
        <FindingCard key={f.ruleId + f.title} finding={f} />
      ))}
    </ul>
  );
}

const VERDICT: Record<Status, string> = {
  OK: "Compatível",
  WARNING: "Atenção",
  INCOMPATIBLE: "Incompatível",
};

/** Load against the recommended wattage and the chosen power supply, on one scale. */
function PowerGauge({ load, recommended, psu }: { load: number; recommended: number; psu: number | null }) {
  const scale = Math.max(psu ?? 0, recommended, load) * 1.1;
  const at = (watts: number) => `${(watts / scale) * 100}%`;
  return (
    <div>
      <div className="relative h-2 rounded-full bg-surface-muted">
        <div className="absolute inset-y-0 left-0 rounded-full bg-primary" style={{ width: at(load) }} />
        <span className="absolute -top-1 h-4 w-px bg-foreground/60" style={{ left: at(recommended) }} aria-hidden />
        {psu != null && <span className="absolute -top-1.5 h-5 w-0.5 rounded-full bg-accent" style={{ left: at(psu) }} aria-hidden />}
      </div>
      <dl className="mt-3 grid grid-cols-3 gap-2 text-xs">
        <div>
          <dt className="text-subtle">Consumo</dt>
          <dd className="font-semibold text-foreground tabular-nums">{load} W</dd>
        </div>
        <div>
          <dt className="text-subtle">Recomendado</dt>
          <dd className="font-semibold text-foreground tabular-nums">{recommended} W</dd>
        </div>
        {psu != null && (
          <div>
            <dt className="text-subtle">Sua fonte</dt>
            <dd className="font-semibold text-accent tabular-nums">{psu} W</dd>
          </div>
        )}
      </dl>
    </div>
  );
}

export function CompatibilityPanel({ compatibility, psuWatts = null }: { compatibility: BuildView["compatibility"]; psuWatts?: number | null }) {
  const [showPassed, setShowPassed] = useState(false);
  const problems = compatibility.findings.filter((finding) => finding.status !== "OK");
  const passed = compatibility.findings.filter((finding) => finding.status === "OK");
  const { Icon, color } = STATUS_ICON[compatibility.overall];

  return (
    <section id="compatibilidade" aria-labelledby="compat-title" className="reveal panel scroll-mt-6 overflow-hidden rounded-2xl">
      {/* Verdict band: the answer first, the energy budget beside it. */}
      <div className="grid gap-6 p-5 sm:p-6 md:grid-cols-[minmax(0,1fr)_minmax(0,300px)] md:items-center">
        <div>
          <h2 id="compat-title" className="text-sm font-semibold text-muted">
            Compatibilidade
          </h2>
          <p className="mt-2 flex items-start gap-2.5 leading-relaxed">
            <Icon className={`mt-0.5 size-5 shrink-0 ${color}`} strokeWidth={2.5} aria-label={VERDICT[compatibility.overall]} role="img" />
            <span>
              {compatibility.overall === "OK" ? "" : `${compatibility.summary} `}
              {passed.length} de {compatibility.findings.length} verificações sem problemas.
            </span>
          </p>
        </div>
        <div className="md:border-l md:border-border md:pl-6">
          <PowerGauge load={compatibility.power.estimatedLoadWatts} recommended={compatibility.power.recommendedPsuWatts} psu={psuWatts} />
          {!compatibility.power.complete && <p className="mt-2 text-xs text-subtle">Estimativa parcial: faltam dados de consumo.</p>}
        </div>
      </div>

      {problems.length > 0 && (
        <div className="border-t border-border px-5 pb-5 sm:px-6 sm:pb-6">
          <FindingGrid findings={problems} />
        </div>
      )}

      {passed.length > 0 && (
        <div className="border-t border-border px-5 py-3 sm:px-6">
          <button
            type="button"
            onClick={() => setShowPassed(!showPassed)}
            aria-expanded={showPassed}
            className="flex items-center gap-2 text-sm font-medium text-muted hover:text-foreground"
          >
            <ChevronIcon className={`size-4 transition-transform ${showPassed ? "rotate-90" : ""}`} />
            Ver as {passed.length} verificações
          </button>
          <Collapse open={showPassed}>
            <div className="pb-2">
              <FindingGrid findings={passed} />
            </div>
          </Collapse>
        </div>
      )}
    </section>
  );
}
