import type { Metadata } from "next";
import { ButtonLink } from "@/components/ui";

export const metadata: Metadata = { title: "Página não encontrada" };

export default function NotFound() {
  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center sm:px-6">
      <p className="text-sm font-medium text-muted">Erro 404</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight">Não encontramos esta página</h1>
      <p className="mt-3 leading-relaxed text-muted">
        O endereço pode ter mudado ou o link estar incompleto. Se era uma configuração salva, confira se o link foi copiado
        inteiro.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <ButtonLink href="/montar">Montar um PC</ButtonLink>
        <ButtonLink href="/" variant="secondary">
          Página inicial
        </ButtonLink>
      </div>
    </div>
  );
}
