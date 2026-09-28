import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/site";

const PAGES = ["/", "/montar", "/verificar", "/melhorar", "/creditos"];

export default function sitemap(): MetadataRoute.Sitemap {
  const base = siteUrl();
  return PAGES.map((path) => ({ url: new URL(path, base).toString(), changeFrequency: "weekly", priority: path === "/" ? 1 : 0.7 }));
}
