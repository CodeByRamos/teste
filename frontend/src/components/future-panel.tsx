"use client";

import { useState } from "react";
import type { FutureAspect, FutureLevel, FutureOutlook } from "@/lib/types";
import { AlertIcon, ArrowIcon, CheckIcon, ChevronIcon } from "./icons";
import { Collapse } from "./ui";

const LEVELS: Record<FutureLevel, { label: string; className: string; Icon: typeof CheckIcon }> = {
  GOOD: { label: "Pode evoluir", className: "text-ok", Icon: CheckIcon },
  PARTIAL: { label: "Pouca folga", className: "text-accent", Icon: ArrowIcon },
  LIMITED: { label: "Exige outras trocas", className: "text-warn", Icon: AlertIcon },
};

function AspectRow({ aspect }: { aspect: FutureAspect }) {
  const [open, setOpen] = useState(false);
  const level = LEVELS[aspect.level];
  return (
    <li className="py-5 first:pt-0">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-semibold tracking-wide text-accent uppercase">{aspect.title}</p>
        <span className={`inline-flex items-center gap-1.5 text-sm font-medium ${level.className}`}>
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
          <Collapse open={open}>
            <p className="mt-1 font-mono text-xs leading-relaxed text-subtle">{aspect.technicalDetail}</p>
          </Collapse>
        </>
      )}
    </li>
  );
}

/** "Thinking about the future": what can be upgraded later without replacing other parts. Content comes from the API. */
export function FuturePanel({ future }: { future: FutureOutlook }) {
  return (
    <section id="futuro" aria-labelledby="future-title" className="reveal scroll-mt-6 border-y border-border py-8">
      <h2 id="future-title" className="sr-only">
        Pensando no futuro
      </h2>
      <ul className="divide-y divide-border">
        {future.aspects.map((aspect) => (
          <AspectRow key={aspect.id} aspect={aspect} />
        ))}
      </ul>
    </section>
  );
}
