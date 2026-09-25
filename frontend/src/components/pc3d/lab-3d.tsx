"use client";

import dynamic from "next/dynamic";
import { useCallback, useEffect, useMemo, useState } from "react";
import { assemble, type BuildModels, type Placement } from "@/lib/models3d/assembly";
import { PRESETS } from "@/lib/models3d/presets";
import type { ModelCatalog } from "@/lib/models3d/spec";
import type { SceneStats } from "./pc-scene";
import { StatusIcon } from "../ui";

const PcScene = dynamic(() => import("./pc-scene").then((m) => m.PcScene), {
  ssr: false,
  loading: () => <div className="grid h-full place-items-center text-sm text-muted">Carregando cena 3D…</div>,
});

const CHECK_STATUS = { ok: "OK", warning: "WARNING", fail: "INCOMPATIBLE" } as const;

export function Lab3d({ extraBuilds = [] }: { extraBuilds?: { id: string; label: string; description: string; build: BuildModels }[] }) {
  const [catalog, setCatalog] = useState<ModelCatalog | null>(null);
  const [presetId, setPresetId] = useState(PRESETS[0].id);
  const [hideSide, setHideSide] = useState(true);
  const [lowDetail, setLowDetail] = useState(false);
  const [selected, setSelected] = useState<Placement | null>(null);
  const [stats, setStats] = useState<SceneStats | null>(null);

  useEffect(() => {
    fetch("/3d-models/catalog.json").then((r) => r.json()).then(setCatalog).catch(() => setCatalog(null));
  }, []);

  const presets = [...PRESETS, ...extraBuilds];
  const preset = presets.find((p) => p.id === presetId) ?? presets[0];
  const assembly = useMemo(() => assemble(preset.build), [preset]);

  const urls = useMemo(() => {
    const map: Record<string, string> = {};
    for (const model of catalog?.models ?? []) {
      const url = lowDetail ? model.lod1Url : model.url;
      if (url) map[model.modelId] = url;
    }
    return map;
  }, [catalog, lowDetail]);

  const onStats = useCallback((s: SceneStats) => setStats(s), []);
  const implemented = catalog?.models.filter((m) => m.status === "implemented") ?? [];
  const usedModels = [...new Set(assembly.placements.map((p) => p.spec.modelId))];
  const bytes = usedModels.reduce((sum, id) => {
    const model = catalog?.models.find((m) => m.modelId === id);
    return sum + (lowDetail ? model?.stats?.lod1Bytes ?? 0 : model?.stats?.bytes ?? 0);
  }, 0);
  const missing = usedModels.filter((id) => !urls[id]);

  return (
    <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_340px]">
      <div>
        <div className="mb-3 flex flex-wrap gap-2" role="tablist" aria-label="Cenários de teste">
          {presets.map((p) => (
            <button
              key={p.id}
              role="tab"
              aria-selected={p.id === preset.id}
              onClick={() => {
                setPresetId(p.id);
                setSelected(null);
              }}
              className={`rounded-full border px-3.5 py-1.5 text-sm transition-colors ${p.id === preset.id ? "border-accent bg-accent text-accent-foreground" : "border-border-strong bg-surface hover:bg-surface-muted"}`}
            >
              {p.label}
            </button>
          ))}
        </div>
        <p className="mb-3 text-sm text-muted">{preset.description}</p>
        <div className="h-[560px] overflow-hidden rounded-2xl border border-border bg-surface">
          {catalog && (
            <PcScene
              assembly={assembly}
              urls={urls}
              selectedKey={selected?.key ?? null}
              onSelect={setSelected}
              hideSidePanel={hideSide}
              onStats={onStats}
            />
          )}
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-5 text-sm">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={hideSide} onChange={(e) => setHideSide(e.target.checked)} className="accent-[var(--accent)]" />
            Esconder tampa de vidro
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={lowDetail} onChange={(e) => setLowDetail(e.target.checked)} className="accent-[var(--accent)]" />
            Nível de detalhe baixo (LOD1)
          </label>
          <span className="text-muted">Arraste para girar · role para aproximar · clique numa peça</span>
        </div>
        {missing.length > 0 && (
          <p className="mt-2 text-sm text-warn">Modelos ainda não gerados: {missing.join(", ")}</p>
        )}
      </div>

      <aside className="space-y-6 text-sm">
        <section className="rounded-2xl border border-border bg-surface p-4">
          <h2 className="font-semibold">Peça selecionada</h2>
          {selected ? (
            <dl className="mt-2 space-y-1">
              <Row label="Modelo base" value={selected.spec.modelId} />
              <Row label="Família" value={selected.spec.family} />
              <Row label="Dimensões" value={dims(selected)} />
              <Row label="Origem das medidas" value={selected.spec.confidence === "measured" ? "dados do componente" : "valores típicos"} />
              {Object.entries(selected.spec.params).map(([key, value]) => (
                <Row key={key} label={key} value={String(value)} />
              ))}
              {selected.spec.assumptions.map((a) => (
                <p key={a} className="text-xs text-subtle">{a}</p>
              ))}
            </dl>
          ) : (
            <p className="mt-2 text-muted">Clique numa peça na cena.</p>
          )}
        </section>

        <section className="rounded-2xl border border-border bg-surface p-4">
          <h2 className="font-semibold">Verificações de montagem</h2>
          <ul className="mt-2 space-y-2">
            {assembly.checks.map((check) => (
              <li key={check.id + check.detail} className="flex gap-2">
                <StatusIcon status={CHECK_STATUS[check.status]} className="size-3" />
                <span>
                  <span className="font-medium">{check.title}</span>
                  <span className="block text-muted">{check.detail}</span>
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-2xl border border-border bg-surface p-4">
          <h2 className="font-semibold">Desempenho</h2>
          <dl className="mt-2 space-y-1">
            <Row label="Triângulos na tela" value={stats ? stats.triangles.toLocaleString("pt-BR") : "…"} />
            <Row label="Draw calls" value={stats ? String(stats.drawCalls) : "…"} />
            <Row label="Modelos distintos" value={String(usedModels.length)} />
            <Row label="Download (GLB)" value={`${(bytes / 1024).toFixed(0)} KB`} />
          </dl>
        </section>

        <section className="rounded-2xl border border-border bg-surface p-4">
          <h2 className="font-semibold">Biblioteca</h2>
          <p className="mt-2 text-muted">
            {implemented.length} modelos base gerados, {(catalog?.models.length ?? 0) - implemented.length} planejados.
          </p>
        </section>
      </aside>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right font-medium">{value}</dd>
    </div>
  );
}

function dims(placement: Placement) {
  const b = placement.layout.bounds;
  const size = [0, 1, 2].map((i) => Math.round(b.max[i] - b.min[i]));
  return `${size[0]} × ${size[1]} × ${size[2]} mm`;
}
