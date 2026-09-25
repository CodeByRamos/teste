import type { Metadata } from "next";
import { PartsChecker } from "@/components/parts-checker";
import { PageHeader } from "@/components/ui";

export const metadata: Metadata = { title: "Verificar peças" };

export default function VerificarPage() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 sm:py-16">
      <PageHeader eyebrow="Tenho um PC" title="Suas peças funcionam juntas?">
        Informe as peças do seu computador — ou as que você está pensando em comprar — e verificamos encaixes, espaço no
        gabinete e energia da fonte.
      </PageHeader>
      <PartsChecker />
    </div>
  );
}
