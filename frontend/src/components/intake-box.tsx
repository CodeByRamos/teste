"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { needsToParams } from "@/lib/needs";
import type { Interpretation, UseCase } from "@/lib/types";
import { ArrowIcon, CheckIcon, InfoIcon, SparkIcon } from "./icons";
import { Button } from "./ui";

const EXAMPLES = [
  "Quero um PC de até 6 mil para jogar, programar e editar vídeo",
  "Tenho R$ 4.000 e quero jogar Valorant e CS2",
  "PC para programar e usar Docker, até 5 mil",
  "Computador para estudar e trabalhar, até R$ 3.000",
];

export function IntakeBox() {
  const router = useRouter();
  const [text, setText] = useState("");
  const [result, setResult] = useState<Interpretation | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function interpret(value = text) {
    if (!value.trim()) return;
    setLoading(true);
    setError(null);
    try {
      setResult(await api.interpret(value));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível entender o pedido agora.");
    } finally {
      setLoading(false);
    }
  }

  function continueToWizard() {
    if (!result) return;
    const params = new URLSearchParams();
    if (result.budgetBrl) params.set("orcamento", String(result.budgetBrl));
    if (result.useCases.length) params.set("usos", result.useCases.map((u) => u.value).join(","));
    if (result.resolution) params.set("resolucao", result.resolution.value);
    const complete = result.budgetBrl && result.useCases.length && !result.mentionsOwnedParts;
    if (complete) {
      const needs = {
        budgetBrl: result.budgetBrl!,
        useCases: result.useCases.map((u) => u.value as UseCase),
        resolution: (result.resolution?.value as never) ?? null,
      };
      router.push(`/montar/resultado?${needsToParams(needs)}`);
    } else {
      router.push(`/montar?${params}`);
    }
  }

  return (
    <div className="rounded-3xl border border-border bg-surface p-3 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_8px_24px_-12px_rgba(0,0,0,0.08)]">
      <form
        onSubmit={(event) => {
          event.preventDefault();
          interpret();
        }}
      >
        <label htmlFor="intake" className="sr-only">
          Descreva o computador que você quer
        </label>
        <textarea
          id="intake"
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              interpret();
            }
          }}
          maxLength={1000}
          rows={3}
          placeholder="Ex.: Quero um PC de até 6 mil para jogar, programar e editar vídeo."
          className="w-full resize-none rounded-2xl bg-transparent px-4 py-3 text-lg leading-relaxed placeholder:text-subtle focus:outline-none"
        />
        <div className="flex items-center justify-between gap-3 px-2 pb-1">
          <p className="hidden text-xs text-subtle sm:block">Escreva do seu jeito. Nós perguntamos só o que faltar.</p>
          <Button type="submit" disabled={loading || !text.trim()} className="ml-auto">
            {loading ? "Entendendo…" : "Continuar"}
            <ArrowIcon className="size-4" />
          </Button>
        </div>
      </form>

      {!result && (
        <div className="flex flex-wrap gap-2 border-t border-border px-2 pt-3 pb-1">
          {EXAMPLES.map((example) => (
            <button
              key={example}
              type="button"
              onClick={() => {
                setText(example);
                interpret(example);
              }}
              className="rounded-full bg-surface-muted px-3 py-1.5 text-left text-sm text-muted transition-colors hover:bg-accent-soft hover:text-foreground"
            >
              {example}
            </button>
          ))}
        </div>
      )}

      {error && <p className="px-3 pb-2 text-sm text-bad">{error}</p>}

      {result && (
        <div className="mt-1 grid gap-4 border-t border-border px-3 pt-4 pb-2 sm:grid-cols-2" aria-live="polite">
          <div>
            <p className="mb-2 flex items-center gap-2 text-sm font-semibold">
              <SparkIcon className="size-4 text-accent" /> Entendemos
            </p>
            {result.understood.length ? (
              <ul className="space-y-1.5 text-sm">
                {result.understood.map((item) => (
                  <li key={item} className="flex gap-2">
                    <CheckIcon className="mt-0.5 size-4 shrink-0 text-ok" />
                    {item}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted">Ainda não identificamos detalhes no texto.</p>
            )}
          </div>
          <div>
            {result.questions.length > 0 && (
              <>
                <p className="mb-2 flex items-center gap-2 text-sm font-semibold">
                  <InfoIcon className="size-4 text-accent" /> Falta saber
                </p>
                <ul className="space-y-1.5 text-sm text-muted">
                  {result.questions.map((question) => (
                    <li key={question}>{question}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <Button onClick={continueToWizard}>
              {result.budgetBrl && result.useCases.length && !result.mentionsOwnedParts ? "Ver meu PC" : "Completar informações"}
              <ArrowIcon className="size-4" />
            </Button>
            <Button variant="ghost" onClick={() => setResult(null)}>
              Reescrever
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
