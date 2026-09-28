"use client";

import { useEffect } from "react";
import { Button, ButtonLink } from "@/components/ui";

/** Unexpected failure while rendering a page. The details stay in the console, never on screen. */
export default function Error({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center sm:px-6">
      <h1 className="text-3xl font-semibold tracking-tight">Algo deu errado</h1>
      <p className="mt-3 leading-relaxed text-muted">
        Não foi culpa sua. Tente de novo; se continuar, volte daqui a alguns minutos.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Button onClick={() => retry()}>Tentar de novo</Button>
        <ButtonLink href="/" variant="secondary">
          Página inicial
        </ButtonLink>
      </div>
    </div>
  );
}
