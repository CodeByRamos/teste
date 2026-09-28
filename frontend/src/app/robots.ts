import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      // Saved builds are private links; the 3D lab and "my builds" are not content pages.
      disallow: ["/api/", "/configuracao/", "/minhas-configuracoes", "/laboratorio-3d"],
    },
    sitemap: new URL("/sitemap.xml", siteUrl()).toString(),
  };
}
