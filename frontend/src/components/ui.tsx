import Link from "next/link";
import type { ComponentProps, ReactNode } from "react";
import type { Status } from "@/lib/types";
import { AlertIcon, CheckIcon, InfoIcon, XIcon } from "./icons";

type Variant = "primary" | "secondary" | "ghost";

const variants: Record<Variant, string> = {
  primary: "bg-brand text-accent-foreground hover:bg-primary-hover",
  secondary: "bg-accent-soft text-accent hover:brightness-125",
  ghost: "text-muted hover:text-foreground hover:bg-surface-muted",
};

const base =
  "inline-flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50";

export function Button({ variant = "primary", className = "", ...props }: ComponentProps<"button"> & { variant?: Variant }) {
  return <button className={`${base} ${variants[variant]} ${className}`} {...props} />;
}

export function ButtonLink({
  variant = "primary",
  className = "",
  ...props
}: ComponentProps<typeof Link> & { variant?: Variant }) {
  return <Link className={`${base} ${variants[variant]} ${className}`} {...props} />;
}

const statusStyle: Record<Status, { className: string; label: string; Icon: typeof CheckIcon }> = {
  OK: { className: "text-ok", label: "Compatível", Icon: CheckIcon },
  WARNING: { className: "text-warn", label: "Atenção", Icon: AlertIcon },
  INCOMPATIBLE: { className: "text-bad", label: "Incompatível", Icon: XIcon },
};

export function StatusBadge({ status, label }: { status: Status; label?: string }) {
  const style = statusStyle[status];
  return (
    <span className={`inline-flex items-center gap-1.5 text-sm font-medium ${style.className}`}>
      <style.Icon className="size-3.5" strokeWidth={2.5} />
      {label ?? style.label}
    </span>
  );
}

export function StatusIcon({ status, className = "size-4" }: { status: Status; className?: string }) {
  const style = statusStyle[status];
  return (
    <span className={`grid shrink-0 place-items-center ${style.className}`} role="img" aria-label={style.label}>
      <style.Icon className={className} strokeWidth={2.5} />
    </span>
  );
}

type CalloutTone = "accent" | "warn" | "bad";

const calloutTones: Record<CalloutTone, { border: string; edge: string; ring: string; title: string }> = {
  accent: {
    border: "border-accent/30",
    edge: "via-accent/70",
    ring: "border-accent/40 bg-accent/10 text-accent",
    title: "text-accent",
  },
  warn: {
    border: "border-warn/30",
    edge: "via-warn/70",
    ring: "border-warn/40 bg-warn/10 text-warn",
    title: "text-warn",
  },
  bad: {
    border: "border-bad/30",
    edge: "via-bad/70",
    ring: "border-bad/40 bg-bad/10 text-bad",
    title: "text-bad",
  },
};

/**
 * The product's notice box: near-black card with a thin tinted border and a glowing top edge, an icon in a ringed
 * circle, an optional small-caps title, the message and an optional footnote.
 */
export function Callout({
  tone = "accent",
  title,
  icon,
  footnote,
  children,
}: {
  tone?: CalloutTone;
  title?: ReactNode;
  icon?: ReactNode;
  footnote?: ReactNode;
  children: ReactNode;
}) {
  const style = calloutTones[tone];
  const Fallback = tone === "accent" ? InfoIcon : AlertIcon;
  return (
    <div
      className={`relative flex gap-4 overflow-hidden rounded-xl border ${style.border} bg-[color-mix(in_oklab,var(--background)_55%,black)] p-4 sm:p-5`}
      role="note"
    >
      <span aria-hidden className={`pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent ${style.edge} to-transparent`} />
      <span aria-hidden className={`grid size-10 shrink-0 place-items-center rounded-full border ${style.ring}`}>
        {icon ?? <Fallback className="size-4.5" />}
      </span>
      <div className="min-w-0 flex-1 self-center">
        {title && <p className={`text-xs font-semibold tracking-wide uppercase ${style.title}`}>{title}</p>}
        <div className={`leading-relaxed text-foreground ${title ? "mt-1.5" : ""}`}>{children}</div>
        {footnote && (
          <p className="mt-3 flex items-start gap-1.5 text-sm text-subtle">
            <InfoIcon className="mt-0.5 size-3.5 shrink-0" />
            <span>{footnote}</span>
          </p>
        )}
      </div>
    </div>
  );
}

/** Short notice in the Callout style: information (violet), warning (amber) or error (red). */
export function Notice({ tone = "info", children }: { tone?: "info" | "warn" | "bad"; children: ReactNode }) {
  return <Callout tone={tone === "info" ? "accent" : tone}>{children}</Callout>;
}

export function PageHeader({ eyebrow, title, children }: { eyebrow?: string; title: string; children?: ReactNode }) {
  return (
    <div className="mb-8 max-w-3xl">
      {eyebrow && <p className="mb-2 text-sm font-medium text-accent">{eyebrow}</p>}
      <h1 className="text-3xl font-semibold tracking-tight text-balance sm:text-4xl">{title}</h1>
      {children && <div className="mt-3 text-lg leading-relaxed text-muted">{children}</div>}
    </div>
  );
}

/**
 * Disclosure body that slides open and closed (height and opacity) instead of popping in. While closed it stays
 * in the page but is inert, so it can't be focused or read.
 */
export function Collapse({ open, id, className = "", children }: { open: boolean; id?: string; className?: string; children: ReactNode }) {
  return (
    <div
      id={id}
      inert={!open}
      className={`grid transition-[grid-template-rows,opacity] duration-300 ease-out ${open ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0"}`}
    >
      <div className="min-h-0 overflow-hidden">
        <div className={className}>{children}</div>
      </div>
    </div>
  );
}

/** Money with a quieter "R$", so the figure itself carries the weight. */
export function Price({ amount }: { amount: number }) {
  const value = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(amount).replace(/^R\$\s*/, "");
  return (
    <>
      <span className="font-normal text-muted">R$</span> {value}
    </>
  );
}

export function Spinner({ label = "Carregando" }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-muted" role="status">
      <span className="size-4 animate-spin rounded-full border-2 border-border-strong border-t-accent" aria-hidden />
      {label}
    </span>
  );
}
