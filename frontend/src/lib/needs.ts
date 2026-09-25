import type { Needs, Resolution, UseCase } from "./types";

// Needs travel in the URL (pt-BR parameter names) so a result page can be shared or reloaded.

const USE_CASES: UseCase[] = [
  "GAMING_COMPETITIVE",
  "GAMING_AAA",
  "PROGRAMMING",
  "CONTAINERS_VMS",
  "VIDEO_EDITING",
  "STREAMING",
  "OFFICE_STUDY",
];
const RESOLUTIONS: Resolution[] = ["FULL_HD", "QHD", "UHD_4K"];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Params = Record<string, string | string[] | undefined>;

function first(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}

export function needsToParams(needs: Needs) {
  const params = new URLSearchParams();
  params.set("orcamento", String(needs.budgetBrl));
  params.set("usos", needs.useCases.join(","));
  if (needs.primaryUse) params.set("principal", needs.primaryUse);
  if (needs.resolution) params.set("resolucao", needs.resolution);
  if (needs.ownedComponentIds?.length) params.set("tenho", needs.ownedComponentIds.join(","));
  return params;
}

/** Partial needs from URL parameters; invalid values are dropped, never guessed. */
export function partialNeedsFromParams(params: Params): Partial<Needs> {
  const budget = Number(first(params.orcamento));
  const uses = (first(params.usos) ?? "")
    .split(",")
    .filter((use): use is UseCase => USE_CASES.includes(use as UseCase));
  const primary = first(params.principal) as UseCase | undefined;
  const resolution = first(params.resolucao) as Resolution | undefined;
  const owned = (first(params.tenho) ?? "").split(",").filter((id) => UUID.test(id));
  return {
    budgetBrl: Number.isFinite(budget) && budget > 0 ? budget : undefined,
    useCases: uses.length ? uses : undefined,
    primaryUse: primary && uses.includes(primary) ? primary : undefined,
    resolution: resolution && RESOLUTIONS.includes(resolution) ? resolution : undefined,
    ownedComponentIds: owned.length ? owned : undefined,
  };
}

export function completeNeeds(partial: Partial<Needs>): Needs | null {
  if (!partial.budgetBrl || !partial.useCases?.length) return null;
  return {
    budgetBrl: partial.budgetBrl,
    useCases: partial.useCases,
    primaryUse: partial.primaryUse ?? null,
    resolution: partial.resolution ?? null,
    ownedComponentIds: partial.ownedComponentIds ?? [],
  };
}
