import Link from "next/link";
import { brand } from "@/config/brand";

const links = [
  { href: "/montar", label: "Montar PC" },
  { href: "/verificar", label: "Verificar peças" },
  { href: "/melhorar", label: "Melhorar meu PC" },
  { href: "/minhas-configuracoes", label: "Minhas configurações" },
];

export function SiteHeader() {
  return (
    <header className="border-b border-border bg-background/90 backdrop-blur supports-[backdrop-filter]:bg-background/75 sticky top-0 z-40">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-6 px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-2.5 font-semibold tracking-tight">
          <span aria-hidden className="grid size-8 place-items-center rounded-lg bg-accent text-accent-foreground">
            <svg viewBox="0 0 24 24" className="size-4.5" fill="none" stroke="currentColor" strokeWidth="2">
              <rect x="6" y="3" width="12" height="18" rx="2" />
              <path d="M9 7h6M9 11h6" strokeLinecap="round" />
              <circle cx="12" cy="16.5" r="1.5" />
            </svg>
          </span>
          <span>{brand.name}</span>
        </Link>
        <nav aria-label="Principal" className="hidden items-center gap-1 text-sm md:flex">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="rounded-lg px-3 py-2 text-muted transition-colors hover:bg-surface-muted hover:text-foreground"
            >
              {link.label}
            </Link>
          ))}
        </nav>
        <Link
          href="/montar"
          className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-foreground transition-colors hover:bg-accent-hover md:hidden"
        >
          Montar PC
        </Link>
      </div>
    </header>
  );
}
