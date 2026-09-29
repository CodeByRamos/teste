"use client";

import { useState } from "react";
import type { BuildView, Finding, Status } from "@/lib/types";
import { AlertIcon, CheckIcon, ChevronIcon, XIcon } from "./icons";
import { Collapse, StatusBadge } from "./ui";

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

export function CompatibilityPanel({ compatibility }: { compatibility: BuildView["compatibility"] }) {
  const [showPassed, setShowPassed] = useState(false);
  const problems = compatibility.findings.filter((finding) => finding.status !== "OK");
  const passed = compatibility.findings.filter((finding) => finding.status === "OK");

  return (
    <section aria-labelledby="compat-title" className="panel rounded-2xl p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="compat-title" className="text-lg font-semibold">
          Compatibilidade
        </h2>
        <StatusBadge status={compatibility.overall} />
      </div>
      <p className="mt-2 leading-relaxed text-muted">{compatibility.summary}</p>

      {problems.length > 0 && <FindingGrid findings={problems} />}

      {passed.length > 0 && (
        <div className="mt-3 border-t border-border pt-3">
          <button
            type="button"
            onClick={() => setShowPassed(!showPassed)}
            aria-expanded={showPassed}
            className="flex items-center gap-2 text-sm font-medium text-muted hover:text-foreground"
          >
            <ChevronIcon className={`size-4 transition-transform ${showPassed ? "rotate-90" : ""}`} />
            {passed.length} verificações sem problemas
          </button>
          <Collapse open={showPassed}>
            <FindingGrid findings={passed} />
          </Collapse>
        </div>
      )}

      <p className="mt-4 border-t border-border pt-4 text-sm text-muted">
        Consumo estimado sob carga: <strong className="text-foreground">{compatibility.power.estimatedLoadWatts} W</strong> · Fonte
        recomendada: <strong className="text-foreground">{compatibility.power.recommendedPsuWatts} W</strong> ou mais
        {!compatibility.power.complete && " (estimativa parcial: faltam dados de consumo)"}.
      </p>
    </section>
  );
}
