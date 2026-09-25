"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { BuildView as Build, SearchResult } from "@/lib/types";
import { BuildView } from "./build-view";
import { XIcon } from "./icons";
import { CATEGORY_LABELS, PartPicker } from "./part-picker";
import { Button, Notice } from "./ui";

export function PartsChecker() {
  const [parts, setParts] = useState<SearchResult[]>([]);
  const [build, setBuild] = useState<Build | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function add(part: SearchResult) {
    setBuild(null);
    setParts((current) => [
      ...current.filter((p) => p.category !== part.category || part.category === "STORAGE"),
      part,
    ]);
  }

  async function check() {
    setLoading(true);
    setError(null);
    try {
      const ids = parts.map((part) => part.id);
      setBuild(await api.evaluate(ids, ids, null));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível verificar agora.");
    } finally {
      setLoading(false);
    }
  }

  if (build) {
    return (
      <BuildView
        build={build}
        title="Resultado da verificação"
        subtitle="Peças informadas por você."
        actions={
          <Button variant="secondary" onClick={() => setBuild(null)}>
            Editar peças
          </Button>
        }
      />
    );
  }

  return (
    <div className="grid gap-8 lg:grid-cols-2">
      <div>
        <h2 className="font-semibold">Adicione as peças</h2>
        <p className="mt-1 text-sm text-muted">Escolha o tipo e busque pelo nome ou modelo.</p>
        <div className="mt-4">
          <PartPicker onPick={add} excludeIds={parts.map((part) => part.id)} />
        </div>
      </div>
      <div>
        <h2 className="font-semibold">Suas peças ({parts.length})</h2>
        {parts.length === 0 ? (
          <p className="mt-4 rounded-2xl border border-dashed border-border-strong p-8 text-center text-sm text-muted">
            Nenhuma peça ainda. Comece pelo processador e pela placa-mãe.
          </p>
        ) : (
          <ul className="mt-4 divide-y divide-border rounded-2xl border border-border bg-surface">
            {parts.map((part) => (
              <li key={part.id} className="flex items-center gap-3 px-4 py-3">
                <span className="min-w-0 flex-1">
                  <span className="block text-xs text-muted">{CATEGORY_LABELS[part.category]}</span>
                  <span className="block truncate text-sm font-medium">{part.name}</span>
                </span>
                <button
                  type="button"
                  onClick={() => setParts(parts.filter((p) => p.id !== part.id))}
                  className="rounded-lg p-1.5 text-muted hover:bg-surface-muted hover:text-foreground"
                  aria-label={`Remover ${part.name}`}
                >
                  <XIcon className="size-4" />
                </button>
              </li>
            ))}
          </ul>
        )}
        {error && (
          <div className="mt-4">
            <Notice tone="bad">{error}</Notice>
          </div>
        )}
        <Button className="mt-6 w-full" onClick={check} disabled={parts.length < 2 || loading}>
          {loading ? "Verificando…" : "Verificar compatibilidade"}
        </Button>
        {parts.length < 2 && <p className="mt-2 text-center text-xs text-subtle">Adicione pelo menos duas peças.</p>}
      </div>
    </div>
  );
}
