import type { Metadata } from "next";
import { PageHeader, Notice } from "@/components/ui";
import { UpgradeScreen } from "@/components/upgrade-screen";
import { api } from "@/lib/api";

export const metadata: Metadata = { title: "Melhorar meu PC" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function MelhorarPage({ searchParams }: PageProps<"/melhorar">) {
  const { pecas } = await searchParams;
  const initialIds = (Array.isArray(pecas) ? pecas[0] : (pecas ?? "")).split(",").filter((id) => UUID.test(id)).slice(0, 16);
  const options = await api.options().catch(() => null);

  return (
    <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 sm:py-16">
      <PageHeader eyebrow="Quero melhorar meu PC" title="O que trocar primeiro?">
        Conte o que você tem e o que quer fazer. Encontramos o que mais limita seu PC e o upgrade que dá mais resultado —
        incluindo tudo o que precisa mudar junto.
      </PageHeader>
      {options ? (
        <UpgradeScreen options={options} initialIds={initialIds} />
      ) : (
        <Notice tone="bad">Não conseguimos carregar as opções agora. Tente novamente em instantes.</Notice>
      )}
    </div>
  );
}
