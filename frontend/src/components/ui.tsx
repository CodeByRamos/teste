import Link from "next/link";
import type { ComponentProps, ReactNode } from "react";
import type { Status } from "@/lib/types";
import { AlertIcon, CheckIcon, InfoIcon, XIcon } from "./icons";

type Variant = "primary" | "secondary" | "ghost";

const variants: Record<Variant, string> = {
  primary: "bg-accent text-accent-foreground hover:bg-accent-hover shadow-sm",
  secondary: "bg-surface text-foreground border border-border-strong hover:bg-surface-muted",
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
  OK: { className: "bg-ok-soft text-ok", label: "Compatível", Icon: CheckIcon },
  WARNING: { className: "bg-warn-soft text-warn", label: "Atenção", Icon: AlertIcon },
  INCOMPATIBLE: { className: "bg-bad-soft text-bad", label: "Incompatível", Icon: XIcon },
};

export function StatusBadge({ status, label }: { status: Status; label?: string }) {
  const style = statusStyle[status];
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${style.className}`}>
      <style.Icon className="size-3.5" strokeWidth={2.5} />
      {label ?? style.label}
    </span>
  );
}

export function StatusIcon({ status, className = "size-4" }: { status: Status; className?: string }) {
  const style = statusStyle[status];
  return (
    <span className={`grid shrink-0 place-items-center rounded-full p-1 ${style.className}`} role="img" aria-label={style.label}>
      <style.Icon className={className} strokeWidth={2.5} />
    </span>
  );
}

export function Notice({ tone = "info", children }: { tone?: "info" | "warn" | "bad"; children: ReactNode }) {
  const tones = {
    info: "bg-accent-soft text-foreground",
    warn: "bg-warn-soft text-foreground",
    bad: "bg-bad-soft text-foreground",
  };
  const iconTones = { info: "text-accent", warn: "text-warn", bad: "text-bad" };
  return (
    <div className={`flex gap-3 rounded-xl px-4 py-3 text-sm leading-relaxed ${tones[tone]}`} role="note">
      {tone === "info" ? (
        <InfoIcon className={`mt-0.5 size-4 shrink-0 ${iconTones[tone]}`} />
      ) : (
        <AlertIcon className={`mt-0.5 size-4 shrink-0 ${iconTones[tone]}`} />
      )}
      <div>{children}</div>
    </div>
  );
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

export function Spinner({ label = "Carregando" }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-muted" role="status">
      <span className="size-4 animate-spin rounded-full border-2 border-border-strong border-t-accent" aria-hidden />
      {label}
    </span>
  );
}
