/**
 * Public URL of the site, for canonical links, sitemap and social previews. NEXT_PUBLIC_SITE_URL wins (custom
 * domain); on Vercel the production domain is known automatically; otherwise local development.
 */
export function siteUrl(): URL {
  const explicit = process.env.NEXT_PUBLIC_SITE_URL;
  if (explicit) return new URL(explicit);
  const vercel = process.env.VERCEL_PROJECT_PRODUCTION_URL;
  if (vercel) return new URL(`https://${vercel}`);
  return new URL("http://localhost:3000");
}
