/**
 * Product identity. The name and visual identity are not defined yet, so everything brand-specific
 * comes from here (and color tokens in globals.css). Set NEXT_PUBLIC_APP_NAME to rename the product.
 */
export const brand = {
  name: process.env.NEXT_PUBLIC_APP_NAME ?? "Configurador de PC",
  tagline: "Diga o que você precisa. Nós cuidamos do resto.",
  description:
    "Monte um computador a partir do que você quer fazer — sem precisar entender de peças. Nós escolhemos, verificamos a compatibilidade e explicamos cada decisão.",
} as const;
