import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { RecommendationScreen } from "@/components/recommendation-screen";
import { completeNeeds, partialNeedsFromParams } from "@/lib/needs";

export const metadata: Metadata = { title: "Seu PC" };

export default async function ResultadoPage({ searchParams }: PageProps<"/montar/resultado">) {
  const params = await searchParams;
  const needs = completeNeeds(partialNeedsFromParams(params));
  if (!needs) {
    redirect(`/montar?${new URLSearchParams(params as Record<string, string>)}`);
  }
  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 sm:py-14">
      <RecommendationScreen needs={needs} />
    </div>
  );
}
