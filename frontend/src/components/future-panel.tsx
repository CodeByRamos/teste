"use client";

import { useState } from "react";
import type { FutureAspect, FutureLevel, FutureOutlook } from "@/lib/types";
import { AlertIcon, ArrowIcon, CheckIcon, ChevronIcon } from "./icons";

const LEVELS: Record<FutureLevel, { label: string; className: string; Icon: typeof CheckIcon }> = {
  GOOD: { label: "Pode evoluir", className: "bg-ok-soft text-ok", Icon: CheckIcon },
  PARTIAL: { label: "Pouca folga", className: "bg-accent-soft text-accent", Icon: ArrowIcon },
  LIMITED: { label: "Exige outras trocas", className: "bg-warn-soft text-warn", Icon: AlertIcon },
};

function AspectRow({ aspect }: { aspect: FutureAspect }) {
  const [open, setOpen] = useState(false);
  const level = LEVELS[aspect.level];
  return (
    <li className="py-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-semibold text-muted">{aspect.title}</p>
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${level.className}`}>
          <level.Icon className="size-3.5" strokeWidth={2.5} />
          {level.label}
        </span>
      </div>
      <p className="mt-1.5 font-medium leading-snug">{aspect.headline}</p>
      <p className="mt-1 text-sm leading-relaxed text-muted">{aspect.explanation}</p>
      {aspect.technicalDetail && (
        <>
          <button
            type="button"
            onClick={() => setOpen(!open)}
            aria-expanded={open}
            className="mt-2 flex items-center gap-1.5 text-xs font-medium text-subtle hover:text-foreground"
          >
            <ChevronIcon className={`size-3.5 transition-transform ${open ? "rotate-90" : ""}`} />
            Detalhes técnicos
          </button>
          {open && <p className="mt-1 font-mono text-xs leading-relaxed text-subtle">{aspect.technicalDetail}</p>}
        </>
      )}
    </li>
  );
}

/** "Thinking about the future": what can be upgraded later without replacing other parts. Content comes from the API. */
export function FuturePanel({ future }: { future: FutureOutlook }) {
  return (
    <section aria-labelledby="future-title" className="rounded-2xl border border-border bg-surface p-5 sm:p-6">
      <h2 id="future-title" className="text-lg font-semibold">
        Pensando no futuro
      </h2>
      <p className="mt-2 leading-relaxed text-muted">{future.summary}</p>
      <ul className="mt-1 divide-y divide-border">
        {future.aspects.map((aspect) => (
          <AspectRow key={aspect.id} aspect={aspect} />
        ))}
      </ul>
      <p className="mt-2 border-t border-border pt-4 text-xs leading-relaxed text-subtle">{future.disclaimer}</p>
    </section>
  );
}
