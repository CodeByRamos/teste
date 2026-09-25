import Link from "next/link";
import { brand } from "@/config/brand";

export function SiteFooter() {
  return (
    <footer className="mt-24 border-t border-border">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-8 text-sm text-muted sm:px-6 md:flex-row md:items-center md:justify-between">
        <p>{brand.name}</p>
        <p className="max-w-2xl md:text-right">
          Contém informações do{" "}
          <a
            href="https://github.com/buildcores/buildcores-open-db"
            className="underline decoration-border-strong underline-offset-2 hover:text-foreground"
            rel="noopener noreferrer"
            target="_blank"
          >
            BuildCores OpenDB
          </a>
          , disponibilizado sob a{" "}
          <a
            href="https://opendatacommons.org/licenses/by/1-0/"
            className="underline decoration-border-strong underline-offset-2 hover:text-foreground"
            rel="noopener noreferrer"
            target="_blank"
          >
            Open Data Commons Attribution License (ODC-By) v1.0
          </a>
          .{" "}
          <Link href="/creditos" className="underline decoration-border-strong underline-offset-2 hover:text-foreground">
            Créditos e fontes de dados
          </Link>
        </p>
      </div>
    </footer>
  );
}
