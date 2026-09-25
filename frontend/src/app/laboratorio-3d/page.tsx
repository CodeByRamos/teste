import type { Metadata } from "next";
import { Lab3d } from "@/components/pc3d/lab-3d";

export const metadata: Metadata = { title: "Laboratório 3D" };

export default function Laboratorio3dPage() {
  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <p className="mb-2 text-sm font-medium text-accent">Interno · biblioteca de modelos</p>
      <h1 className="text-3xl font-semibold tracking-tight">Laboratório 3D</h1>
      <p className="mt-2 max-w-3xl text-muted">
        Cena de teste da biblioteca paramétrica: cada peça usa um modelo base da família, adaptado às dimensões do
        componente, e é montada pelos pontos de montagem do gabinete e da placa-mãe. As verificações abaixo usam as
        mesmas medidas.
      </p>
      <Lab3d />
    </div>
  );
}
