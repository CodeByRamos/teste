"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { needsToParams } from "@/lib/needs";
import { rememberSavedBuild } from "@/lib/saved-builds";
import type { Alternative, BuildItem, BuildView as Build, Needs } from "@/lib/types";
import { BuildView } from "./build-view";
import { Button, ButtonLink, Notice, Spinner } from "./ui";

const LOADING_STEPS = [
  "Transformando seu pedido em requisitos técnicos…",
  "Comparando processadores e placas de vídeo…",
  "Verificando encaixes, espaço e energia…",
];

function failure(error: unknown) {
  return error instanceof ApiError ? error.message : "Não foi possível montar a configuração.";
}

export function RecommendationScreen({ needs }: { needs: Needs }) {
  const router = useRouter();
  const [build, setBuild] = useState<Build | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [saving, setSaving] = useState(false);
  const [loadingStep, setLoadingStep] = useState(0);
  const [swapped, setSwapped] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.recommend(needs).then(
      (result) => !cancelled && setBuild(result),
      (e) => !cancelled && setError(failure(e)),
    );
    return () => {
      cancelled = true;
    };
  }, [needs]);

  function load() {
    setError(null);
    setBuild(null);
    setSwapped(false);
    setLoadingStep(0);
    api.recommend(needs).then(setBuild, (e) => setError(failure(e)));
  }

  useEffect(() => {
    if (build || error) return;
    const timer = setInterval(() => setLoadingStep((step) => Math.min(step + 1, LOADING_STEPS.length - 1)), 900);
    return () => clearInterval(timer);
  }, [build, error]);

  const ownedIds = needs.ownedComponentIds ?? [];

  async function swap(item: BuildItem, alternative: Alternative) {
    if (!build) return;
    setBusy(true);
    try {
      const ids = build.items.map((current) => (current.component.id === item.component.id ? alternative.component.id : current.component.id));
      setBuild(await api.evaluate(ids, ownedIds, needs));
      setSwapped(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível trocar a peça.");
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    if (!build) return;
    setSaving(true);
    try {
      const title = build.needs?.useCases.map((use) => use.label).join(", ") ?? "Minha configuração";
      const { id } = await api.save(
        build.items.map((item) => item.component.id),
        ownedIds,
        needs,
        title,
      );
      rememberSavedBuild({ id, title, totalBrl: build.totals.totalBrl, savedAt: new Date().toISOString() });
      router.push(`/configuracao/${id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível salvar.");
      setSaving(false);
    }
  }

  if (error && !build) {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <Notice tone="bad">{error}</Notice>
        <div className="flex gap-3">
          <Button onClick={load}>Tentar de novo</Button>
          <ButtonLink href={`/montar?${needsToParams(needs)}`} variant="secondary">
            Ajustar respostas
          </ButtonLink>
        </div>
      </div>
    );
  }

  if (!build) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-24 text-center" aria-live="polite">
        <span className="size-10 animate-spin rounded-full border-[3px] border-border border-t-accent" aria-hidden />
        <p className="text-lg font-medium">{LOADING_STEPS[loadingStep]}</p>
        <p className="text-sm text-muted">Avaliamos milhares de combinações para achar o melhor equilíbrio.</p>
      </div>
    );
  }

  return (
    <BuildView
      build={build}
      busy={busy}
      onSwap={swap}
      subtitle={swapped ? "Você trocou peças — preços e compatibilidade foram recalculados." : "Montado a partir do que você nos contou."}
      actions={
        <>
          <Button onClick={save} disabled={saving}>
            {saving ? <Spinner label="Salvando…" /> : "Salvar configuração"}
          </Button>
          {swapped && (
            <Button variant="secondary" onClick={load}>
              Voltar à recomendação original
            </Button>
          )}
          <ButtonLink href={`/montar?${needsToParams(needs)}`} variant="ghost">
            Ajustar respostas
          </ButtonLink>
          {error && <p className="w-full text-sm text-bad">{error}</p>}
        </>
      }
    />
  );
}
