import type { Metadata } from "next";
import { SavedBuildsList } from "@/components/saved-builds-list";
import { PageHeader } from "@/components/ui";

export const metadata: Metadata = { title: "Minhas configurações" };

export default function MinhasConfiguracoesPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-12 sm:px-6 sm:py-16">
      <PageHeader title="Minhas configurações">
        Configurações que você salvou neste navegador. Cada uma tem um link próprio que você pode guardar ou compartilhar.
      </PageHeader>
      <SavedBuildsList />
    </div>
  );
}
