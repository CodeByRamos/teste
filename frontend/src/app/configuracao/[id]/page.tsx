import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { BuildView } from "@/components/build-view";
import { CopyLinkButton } from "@/components/copy-link-button";
import { ButtonLink, Notice } from "@/components/ui";
import { api, ApiError } from "@/lib/api";
import { dateTime } from "@/lib/format";

export const metadata: Metadata = { title: "Configuração salva", robots: { index: false, follow: false } };

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
    <div className="mx-auto max-w-[1600px] px-4 pt-6 pb-10 sm:px-6 sm:pt-8 sm:pb-14 lg:px-10">
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
