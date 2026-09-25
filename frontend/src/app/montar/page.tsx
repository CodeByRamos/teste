import type { Metadata } from "next";
import { BuildWizard } from "@/components/build-wizard";
import { Notice } from "@/components/ui";
import { api } from "@/lib/api";
import { partialNeedsFromParams } from "@/lib/needs";

export const metadata: Metadata = { title: "Montar meu PC" };

export default async function MontarPage({ searchParams }: PageProps<"/montar">) {
  const initial = partialNeedsFromParams(await searchParams);
  const options = await api.options().catch(() => null);

  return (
    <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 sm:py-16">
      {options ? (
        <BuildWizard options={options} initial={initial} />
      ) : (
        <div className="mx-auto max-w-2xl">
          <Notice tone="bad">Não conseguimos carregar as opções agora. Tente novamente em instantes.</Notice>
        </div>
      )}
    </div>
  );
}
