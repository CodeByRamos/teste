import type { Metadata } from "next";
import { Notice, PageHeader } from "@/components/ui";
import { api } from "@/lib/api";
import { dateTime } from "@/lib/format";

export const metadata: Metadata = { title: "Créditos e fontes de dados" };

const CATEGORY_LABELS: Record<string, string> = {
  CPU: "Processadores",
  GPU: "Placas de vídeo",
  MOTHERBOARD: "Placas-mãe",
  MEMORY: "Memórias RAM",
  STORAGE: "Armazenamento",
  POWER_SUPPLY: "Fontes",
  CASE: "Gabinetes",
  CPU_COOLER: "Coolers",
};

export default async function CreditosPage() {
  const sources = await api.dataSources().catch(() => null);

  return (
    <div className="mx-auto max-w-3xl px-4 py-12 sm:px-6 sm:py-16">
      <PageHeader title="Créditos e fontes de dados">
        De onde vêm as informações que usamos, e o que ainda não temos.
      </PageHeader>

      <section className="space-y-4 leading-relaxed">
        <h2 className="text-xl font-semibold">Especificações técnicas</h2>
        <p>
          Contém informações do{" "}
          <a className="underline underline-offset-2" href="https://github.com/buildcores/buildcores-open-db" target="_blank" rel="noopener noreferrer">
            BuildCores OpenDB
          </a>
          , disponibilizado sob a{" "}
          <a className="underline underline-offset-2" href="https://opendatacommons.org/licenses/by/1-0/" target="_blank" rel="noopener noreferrer">
            Open Data Commons Attribution License (ODC-By) v1.0
          </a>
          . Usamos uma versão fixa da base, validamos cada registro e descartamos valores implausíveis antes de usá-los.
        </p>
        {sources && (
          <p className="text-sm text-muted">
            Versão em uso: <span className="font-mono">{sources.hardware.version.slice(0, 12)}</span>
            {sources.hardware.ingestedAt && <> · importada em {dateTime(sources.hardware.ingestedAt)}</>} ·{" "}
            {sources.components.toLocaleString("pt-BR")} componentes.
          </p>
        )}
      </section>

      {sources?.quality && (
        <section className="mt-12">
          <h2 className="text-xl font-semibold">Completude dos dados</h2>
          <p className="mt-2 text-muted">
            A base é comunitária e alguns campos faltam. Quando um dado necessário não existe, avisamos na verificação de
            compatibilidade em vez de supor.
          </p>
          <table className="mt-4 w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted">
                <th className="py-2 font-medium">Categoria</th>
                <th className="py-2 text-right font-medium">Registros</th>
                <th className="py-2 text-right font-medium">Dados completos (média)</th>
              </tr>
            </thead>
            <tbody>
              {Object.keys(CATEGORY_LABELS)
                .filter((category) => sources.quality?.[category])
                .map((category) => [category, sources.quality![category]] as const)
                .map(([category, quality]) => (
                <tr key={category} className="border-b border-border">
                  <td className="py-2">{CATEGORY_LABELS[category] ?? category}</td>
                  <td className="py-2 text-right tabular-nums">{quality.records.toLocaleString("pt-BR")}</td>
                  <td className="py-2 text-right tabular-nums">{Math.round(quality.averageQuality * 100)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <section className="mt-12 space-y-4 leading-relaxed">
        <h2 className="text-xl font-semibold">Preços</h2>
        <Notice tone="warn">
          {sources?.pricesDisclaimer ??
            "Os preços desta versão são fictícios, usados apenas para demonstrar o funcionamento. Ainda não consultamos lojas reais."}
        </Notice>
        <p className="text-muted">
          Preços e disponibilidade virão de lojas e parceiros brasileiros, em uma fonte separada das especificações técnicas.
        </p>
      </section>

      <section className="mt-12 space-y-4 leading-relaxed">
        <h2 className="text-xl font-semibold">Desempenho estimado</h2>
        <p className="text-muted">
          As comparações de desempenho entre peças são estimativas calculadas a partir das especificações técnicas (núcleos,
          frequência e cache), não resultados de testes reais. Elas servem para ordenar opções, não para prever quadros por
          segundo.
        </p>
      </section>
    </div>
  );
}
