import type { Metadata } from "next";
import { connection } from "next/server";
import { Inter, JetBrains_Mono, Krub } from "next/font/google";
import { SiteHeader } from "@/components/site-header";
import { SiteFooter } from "@/components/site-footer";
import { brand } from "@/config/brand";
import { siteUrl } from "@/lib/site";
import "./globals.css";

// Headings in Inter, body copy in Krub.
const sans = Krub({ variable: "--font-sans-family", subsets: ["latin"], weight: ["400", "500", "600", "700"] });
const mono = JetBrains_Mono({ variable: "--font-mono-family", subsets: ["latin"] });
const display = Inter({ variable: "--font-display-family", subsets: ["latin"], style: ["normal", "italic"] });

export const metadata: Metadata = {
  metadataBase: siteUrl(),
  title: { default: brand.name, template: `%s · ${brand.name}` },
  description: brand.description,
  openGraph: { type: "website", locale: "pt_BR", siteName: brand.name, title: brand.name, description: brand.description },
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  // Rendered per request: the Content-Security-Policy nonce (src/proxy.ts) must be fresh on every page.
  await connection();
  return (
    <html lang="pt-BR" className={`${sans.variable} ${mono.variable} ${display.variable} h-full`}>
      <body className="flex min-h-full flex-col">
        <a
          href="#conteudo"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-surface focus:px-4 focus:py-2"
        >
          Pular para o conteúdo
        </a>
        <SiteHeader />
        <main id="conteudo" className="flex-1">
          {children}
        </main>
        <SiteFooter />
      </body>
    </html>
  );
}
