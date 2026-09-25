"use client";

import { useState } from "react";
import type { BuildView, Finding } from "@/lib/types";
import { ChevronIcon } from "./icons";
import { StatusBadge, StatusIcon } from "./ui";

function FindingRow({ finding }: { finding: Finding }) {
  return (
    <li className="flex gap-3 py-3">
      <StatusIcon status={finding.status} className="size-3.5" />
      <div className="min-w-0">
        <p className="font-medium">{finding.title}</p>
        <p className="mt-0.5 text-sm leading-relaxed text-muted">{finding.explanation}</p>
        {finding.technicalDetail && <p className="mt-1 font-mono text-xs text-subtle">{finding.technicalDetail}</p>}
        {!finding.verified && (
          <p className="mt-1 text-xs text-subtle">Não foi possível confirmar com os dados disponíveis.</p>
        )}
      </div>
    </li>
  );
}

export function CompatibilityPanel({ compatibility }: { compatibility: BuildView["compatibility"] }) {
  const [showPassed, setShowPassed] = useState(false);
  const problems = compatibility.findings.filter((finding) => finding.status !== "OK");
  const passed = compatibility.findings.filter((finding) => finding.status === "OK");

  return (
    <section aria-labelledby="compat-title" className="rounded-2xl border border-border bg-surface p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="compat-title" className="text-lg font-semibold">
          Compatibilidade
        </h2>
        <StatusBadge status={compatibility.overall} />
      </div>
      <p className="mt-2 leading-relaxed text-muted">{compatibility.summary}</p>

      {problems.length > 0 && <ul className="mt-2 divide-y divide-border">{problems.map((f) => <FindingRow key={f.ruleId + f.title} finding={f} />)}</ul>}

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
          {showPassed && <ul className="mt-1 divide-y divide-border">{passed.map((f) => <FindingRow key={f.ruleId + f.title} finding={f} />)}</ul>}
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
