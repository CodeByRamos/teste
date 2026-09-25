"use client";

import dynamic from "next/dynamic";
import { useEffect, useMemo, useState } from "react";
import { assemble } from "@/lib/models3d/assembly";
import { categoryOfSlot, loadModelCatalog, toBuildModels } from "@/lib/models3d/from-build";
import type { ModelCatalog } from "@/lib/models3d/spec";
import type { BuildItem, Category } from "@/lib/types";

const PcScene = dynamic(() => import("./pc-scene").then((m) => m.PcScene), {
  ssr: false,
  loading: () => <div className="grid h-full place-items-center text-sm text-muted">Carregando 3D…</div>,
});

/** The build drawn in 3D from the parametric model library. Clicking a part selects it in the list. */
export function Build3d({ items, selected, onSelect }: { items: BuildItem[]; selected: Category | null; onSelect: (category: Category) => void }) {
  const [catalog, setCatalog] = useState<ModelCatalog | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    loadModelCatalog().then(setCatalog, () => setFailed(true));
  }, []);

  const models = useMemo(() => (catalog ? toBuildModels(items, catalog) : null), [catalog, items]);
  const assembly = useMemo(() => (models ? assemble(models) : null), [models]);
  const urls = useMemo(
    () => Object.fromEntries((catalog?.models ?? []).filter((m) => m.url).map((m) => [m.modelId, m.url!])),
    [catalog],
  );

  if (failed) return <p className="p-4 text-sm text-muted">A visualização 3D não está disponível agora.</p>;
  if (!catalog) return <div className="grid h-full place-items-center text-sm text-muted">Carregando 3D…</div>;
  if (!assembly) {
    return <p className="p-4 text-sm text-muted">Para ver em 3D, a configuração precisa ter gabinete e placa-mãe.</p>;
  }
  const selectedKey = selected === null ? null : assembly.placements.find((p) => categoryOfSlot(p.category) === selected)?.key ?? null;
  return (
    <PcScene
      assembly={assembly}
      urls={urls}
      selectedKey={selectedKey}
      hideSidePanel
      onSelect={(placement) => {
        const category = placement ? categoryOfSlot(placement.category) : null;
        if (category) onSelect(category);
      }}
    />
  );
}
