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
    <div className="mx-auto max-w-[1600px] px-4 pt-6 pb-10 sm:px-6 sm:pt-8 sm:pb-14 lg:px-10">
      <RecommendationScreen needs={needs} />
    </div>
  );
}
