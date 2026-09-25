"use client";

import Link from "next/link";
import { useSyncExternalStore } from "react";
import { brl, dateTime } from "@/lib/format";
import { forgetSavedBuild, listSavedBuilds, type SavedBuildEntry } from "@/lib/saved-builds";
import { ChevronIcon, XIcon } from "./icons";
import { ButtonLink } from "./ui";

// Minimal external store over localStorage so the list renders only on the client.
let listeners: (() => void)[] = [];
let snapshot: SavedBuildEntry[] | null = null;
const EMPTY: SavedBuildEntry[] = [];

function subscribe(listener: () => void) {
  listeners.push(listener);
  return () => {
    listeners = listeners.filter((l) => l !== listener);
  };
}

function getSnapshot() {
  snapshot ??= listSavedBuilds();
  return snapshot;
}

function refresh() {
  snapshot = listSavedBuilds();
  listeners.forEach((listener) => listener());
}

export function SavedBuildsList() {
  const entries = useSyncExternalStore(subscribe, getSnapshot, () => EMPTY);

  if (entries.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-border-strong p-10 text-center">
        <p className="text-muted">Você ainda não salvou nenhuma configuração.</p>
        <ButtonLink href="/montar" className="mt-4">
          Montar meu PC
        </ButtonLink>
      </div>
    );
  }

  return (
    <ul className="divide-y divide-border rounded-2xl border border-border bg-surface">
      {entries.map((entry) => (
        <li key={entry.id} className="flex items-center">
          <Link href={`/configuracao/${entry.id}`} className="flex min-w-0 flex-1 items-center gap-4 px-5 py-4 hover:bg-surface-muted/60">
            <span className="min-w-0 flex-1">
              <span className="block truncate font-medium">{entry.title}</span>
              <span className="block text-sm text-muted">Salva em {dateTime(entry.savedAt)}</span>
            </span>
            <span className="font-semibold tabular-nums">{brl(entry.totalBrl)}</span>
            <ChevronIcon className="size-4 text-subtle" />
          </Link>
          <button
            type="button"
            onClick={() => {
              forgetSavedBuild(entry.id);
              refresh();
            }}
            className="mr-3 rounded-lg p-2 text-muted hover:bg-surface-muted hover:text-foreground"
            aria-label={`Remover ${entry.title} desta lista`}
            title="Remover desta lista (o link continua funcionando)"
          >
            <XIcon className="size-4" />
          </button>
        </li>
      ))}
    </ul>
  );
}
