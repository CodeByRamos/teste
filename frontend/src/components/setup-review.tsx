import type { BuildItem, BuildView } from "@/lib/types";
import { CopyTextButton } from "./copy-link-button";
import { AlertIcon, CheckIcon, InfoIcon } from "./icons";
import { IsoPart } from "./pc-diagram";
import { Price } from "./ui";

function cost(item: BuildItem) {
  return item.owned ? 0 : (item.price?.amountBrl ?? 0);
}

/**
 * "Sobre o PC": a short review of the setup, written only from what the engines already decided (totals,
 * compatibility checks, the future outlook, notes). Nothing here is invented: every line points back to a result.
 */
export function SetupReview({ build }: { build: BuildView }) {
  const { totals, compatibility, future, notes } = build;
  const psu = Number.parseInt(
    build.items.find((item) => item.category === "POWER_SUPPLY")?.specs.find((spec) => spec.label === "Potência")?.value ?? "",
    10,
  );
  const headroom = Number.isFinite(psu) && psu > 0 ? psu - compatibility.power.estimatedLoadWatts : null;
  const invested = [...build.items].filter((item) => cost(item) > 0).sort((a, b) => cost(b) - cost(a)).slice(0, 2);
  const strengths = future?.aspects.filter((aspect) => aspect.level === "GOOD") ?? [];
  const observations = [...(totals.allPriced ? [] : ["Algumas peças ainda não têm preço disponível e não entram no total."]), ...notes];

  return (
    <div className="panel space-y-8 rounded-2xl p-5 sm:p-6">
      {/* The setup in three numbers. */}
      <dl className="grid grid-cols-2 gap-x-8 gap-y-5 sm:grid-cols-3">
        <Figure label="Total">
          <Price amount={totals.totalBrl} />
        </Figure>
        {totals.budgetBrl != null && (
          <Figure label="Do orçamento">{Math.round((totals.totalBrl / totals.budgetBrl) * 100)}%</Figure>
        )}
        <Figure label="Folga da fonte">{headroom != null ? `${headroom} W` : "-"}</Figure>
      </dl>

      <div className="grid gap-8 md:grid-cols-2">
        <section aria-labelledby="review-config">
          <div className="flex items-center justify-between gap-3">
            <h3 id="review-config" className="font-semibold">
              Configurações
            </h3>
            <span className="mr-6">
              <CopyTextButton text={specLines(build.items).map(([label, value]) => `${label}: ${value}`).join("\n")} />
            </span>
          </div>
          <dl id="review-config-list" className="mt-3 space-y-1.5 text-sm leading-relaxed">
            {specLines(build.items).map(([label, value]) => (
              <div key={label} className="flex gap-1.5">
                <dt className="shrink-0 font-semibold">{label}:</dt>
                <dd className="min-w-0 text-muted">{value}</dd>
              </div>
            ))}
          </dl>
        </section>

        {(strengths.length > 0 || (headroom != null && headroom > 0)) && (
          <section aria-labelledby="review-fortes">
            <h3 id="review-fortes" className="font-semibold">
              Pontos fortes
            </h3>
            <ul className="mt-3 space-y-3 text-sm leading-relaxed">
              {headroom != null && headroom > 0 && (
                <Point tone="ok">
                  A fonte tem {headroom} W de folga sobre o consumo estimado de {compatibility.power.estimatedLoadWatts} W.
                </Point>
              )}
              {strengths.map((aspect) => (
                <Point key={aspect.id} tone="ok">
                  {aspect.headline}
                </Point>
              ))}
            </ul>
          </section>
        )}
      </div>

      {invested.length > 0 && (
        <section aria-labelledby="review-investimento">
          <h3 id="review-investimento" className="font-semibold">
            Onde está o investimento
          </h3>
          <ul className="mt-3 space-y-4">
            {invested.map((item) => (
              <li key={item.category} className="flex gap-4">
                <IsoPart category={item.category} className="size-12 shrink-0" />
                <div className="min-w-0">
                  <p className="text-sm text-muted">
                    {item.categoryLabel} · {Math.round((cost(item) / totals.totalBrl) * 100)}% do total
                  </p>
                  <p className="font-medium">{item.component.name}</p>
                  <p className="mt-1 text-sm leading-relaxed text-muted">{item.explanation.reason}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {observations.length > 0 && (
        <section aria-labelledby="review-obs" className="border-t border-border pt-6">
          <h3 id="review-obs" className="flex items-center gap-2 font-semibold">
            <InfoIcon className="size-4 text-accent" /> Observações
          </h3>
          <ul className="mt-3 space-y-2 text-sm leading-relaxed text-muted">
            {observations.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

const UNKNOWN = "Não informado";

function specOf(item: BuildItem | undefined, label: string) {
  const value = item?.specs.find((spec) => spec.label === label)?.value;
  return value && value !== UNKNOWN ? value : null;
}

/** "32 GB (2 × 16 GB)" -> "32GB (2x16GB)", "1 TB" -> "1TB", "650 W" -> "650W". */
function compact(value: string) {
  return value.replace(/(\d)\s+(GB|TB|W)(?![a-z])/gi, "$1$2").replace(/\s*×\s*/g, "x");
}

/**
 * The spec sheet in the short one-line-per-part format people share ("CPU: AMD Ryzen 5 5600X", "RAM: 16GB (2x8GB)
 * 3200MHz", "Fonte: 650W 80 Plus Bronze"), built from each part's specs, falling back to its name.
 */
function specLines(items: BuildItem[]): [string, string][] {
  const find = (category: BuildItem["category"]) => items.find((item) => item.category === category);
  const lines: [string, string][] = [];
  const cpu = find("CPU");
  if (cpu) lines.push(["CPU", cpu.component.name]);
  const gpu = find("GPU");
  if (gpu) {
    const chip = specOf(gpu, "Chip");
    const vram = specOf(gpu, "Memória de vídeo")?.match(/(\d+)\s*GB/)?.[1];
    lines.push(["GPU", chip ? `${chip}${vram ? ` ${vram}GB` : ""}` : gpu.component.name]);
  }
  const ram = find("MEMORY");
  if (ram) {
    const capacity = specOf(ram, "Capacidade");
    const speed = specOf(ram, "Velocidade")?.replace(/\s*MT\/s/, "MHz");
    lines.push(["RAM", capacity ? [compact(capacity), speed].filter(Boolean).join(" ") : ram.component.name]);
  }
  const board = find("MOTHERBOARD");
  if (board) lines.push(["Placa-Mãe", board.component.name]);
  const storage = find("STORAGE");
  if (storage) {
    const capacity = specOf(storage, "Capacidade");
    const type = specOf(storage, "Tipo");
    const m2 = specOf(storage, "Formato")?.includes("M.2");
    const kind = type ? (m2 ? type.replace(/^SSD\s*/, "SSD M.2 ") : type) : null;
    lines.push(["Armazenamento", capacity && kind ? `${compact(capacity)} ${kind}` : storage.component.name]);
  }
  const psu = find("POWER_SUPPLY");
  if (psu) {
    const watts = specOf(psu, "Potência");
    const rating = specOf(psu, "Eficiência")?.replace("80+", "80 Plus");
    lines.push(["Fonte", watts ? [compact(watts), rating].filter(Boolean).join(" ") : psu.component.name]);
  }
  const cooler = find("CPU_COOLER");
  if (cooler) lines.push(["Cooler", cooler.component.name]);
  const pcCase = find("CASE");
  if (pcCase) lines.push(["Gabinete", pcCase.component.name]);
  return lines;
}

function Figure({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="mt-0.5 font-display text-2xl font-bold tracking-tight tabular-nums">{children}</dd>
    </div>
  );
}

function Point({ tone, children }: { tone: "ok" | "warn"; children: React.ReactNode }) {
  const Icon = tone === "ok" ? CheckIcon : AlertIcon;
  return (
    <li className="flex gap-2.5">
      <Icon className={`mt-0.5 size-4 shrink-0 ${tone === "ok" ? "text-ok" : "text-warn"}`} strokeWidth={2.5} aria-hidden />
      <span>{children}</span>
    </li>
  );
}
