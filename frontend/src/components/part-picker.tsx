"use client";

import { useEffect, useId, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Category, SearchResult } from "@/lib/types";
import { PlusIcon, SearchIcon } from "./icons";

export const CATEGORY_LABELS: Record<Category, string> = {
  CPU: "Processador",
  GPU: "Placa de vídeo",
  MOTHERBOARD: "Placa-mãe",
  MEMORY: "Memória RAM",
  STORAGE: "Armazenamento",
  POWER_SUPPLY: "Fonte de alimentação",
  CASE: "Gabinete",
  CPU_COOLER: "Cooler do processador",
};

const PLACEHOLDERS: Record<Category, string> = {
  CPU: "Ex.: Ryzen 5 5600, i5 12400",
  GPU: "Ex.: RTX 3060, RX 6600",
  MOTHERBOARD: "Ex.: B550, B760",
  MEMORY: "Ex.: DDR4 16GB",
  STORAGE: "Ex.: 1TB NVMe",
  POWER_SUPPLY: "Ex.: 650W",
  CASE: "Ex.: Mid Tower",
  CPU_COOLER: "Ex.: Peerless Assassin",
};

/** Search the hardware catalog by category and pick one part. */
export function PartPicker({
  onPick,
  excludeIds = [],
  categories = Object.keys(CATEGORY_LABELS) as Category[],
}: {
  onPick: (part: SearchResult) => void;
  excludeIds?: string[];
  categories?: Category[];
}) {
  const id = useId();
  const [category, setCategory] = useState<Category>(categories[0]);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResult[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const searching = query.trim().length >= 2;

  useEffect(() => {
    if (query.trim().length < 2) return;
    let cancelled = false;
    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const found = await api.search(category, query.trim(), 8);
        if (!cancelled) {
          setResults(found);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : "Erro ao buscar peças.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 250);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [category, query]);

  return (
    <div className="rounded-2xl border border-border bg-surface">
      <div className="flex flex-col gap-2 border-b border-border p-3 sm:flex-row">
        <label htmlFor={`${id}-category`} className="sr-only">
          Tipo de peça
        </label>
        <select
          id={`${id}-category`}
          value={category}
          onChange={(event) => setCategory(event.target.value as Category)}
          className="rounded-xl border border-border bg-surface-muted px-3 py-2.5 text-sm"
        >
          {categories.map((value) => (
            <option key={value} value={value}>
              {CATEGORY_LABELS[value]}
            </option>
          ))}
        </select>
        <div className="relative flex-1">
          <SearchIcon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-subtle" />
          <label htmlFor={`${id}-query`} className="sr-only">
            Nome ou modelo
          </label>
          <input
            id={`${id}-query`}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={PLACEHOLDERS[category]}
            maxLength={100}
            autoComplete="off"
            className="w-full rounded-xl border border-border bg-surface py-2.5 pr-3 pl-9 text-sm placeholder:text-subtle"
          />
        </div>
      </div>
      <div className="max-h-80 overflow-y-auto" aria-live="polite">
        {error && <p className="p-4 text-sm text-bad">{error}</p>}
        {!error && searching && !loading && results.length === 0 && (
          <p className="p-4 text-sm text-muted">Nenhuma peça encontrada com esse nome.</p>
        )}
        {!searching && <p className="p-4 text-sm text-muted">Digite pelo menos 2 letras do nome ou modelo.</p>}
        <ul className="divide-y divide-border">
          {(searching ? results : [])
            .filter((part) => !excludeIds.includes(part.id))
            .map((part) => (
              <li key={part.id}>
                <button
                  type="button"
                  onClick={() => onPick(part)}
                  aria-label={`Adicionar ${part.name}`}
                  className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-accent-soft/50"
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{part.name}</span>
                    <span className="mt-0.5 block truncate text-xs text-muted">
                      {part.highlights.map((spec) => `${spec.label}: ${spec.value}`).join(" · ")}
                    </span>
                  </span>
                  <PlusIcon className="size-4 shrink-0 text-accent" />
                </button>
              </li>
            ))}
        </ul>
      </div>
    </div>
  );
}
