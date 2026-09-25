import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { BuildView } from "@/components/build-view";
import { CopyLinkButton } from "@/components/copy-link-button";
import { ButtonLink, Notice } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { dateTime } from "@/lib/format";

export const metadata: Metadata = { title: "Configuração salva" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function SavedBuildPage({ params }: PageProps<"/configuracao/[id]">) {
  const { id } = await params;
  if (!UUID.test(id)) notFound();

  let document;
  try {
    document = await api.saved(id);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) notFound();
    return (
      <div className="mx-auto max-w-2xl px-4 py-16">
        <Notice tone="bad">Não conseguimos carregar esta configuração agora. Tente novamente em instantes.</Notice>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-14">
      <BuildView
        build={document.build}
        title={document.title ?? "Configuração salva"}
        subtitle={
          <>
            Salva em {dateTime(document.savedAt)}. Preços e compatibilidade como estavam nesse momento.
          </>
        }
        actions={
          <>
            <CopyLinkButton />
            <ButtonLink href="/montar" variant="secondary">
              Montar outra
            </ButtonLink>
          </>
        }
      />
    </div>
  );
}
