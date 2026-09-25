import type { NextRequest } from "next/server";

/**
 * The browser only talks to this origin; this handler relays /api/* to the Java backend server-side.
 * It adds the shared secret (FRONTEND_SHARED_SECRET) that lets the backend answer only this site, and the
 * visitor's address so the backend's rate limit counts each visitor separately. Browser cookies and other
 * headers are not forwarded.
 */

const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";
const RESPONSE_HEADERS = ["content-type", "retry-after"];

/** Vercel (and most hosts) overwrite these headers with the real visitor address, so the browser cannot forge them. */
function visitorAddress(request: NextRequest): string | null {
  const realIp = request.headers.get("x-real-ip")?.trim();
  if (realIp) return realIp;
  return request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || null;
}

async function relay(request: NextRequest, ctx: RouteContext<"/api/[...path]">): Promise<Response> {
  const { path } = await ctx.params;
  if (path.some((segment) => segment === "." || segment === ".." || segment === "")) {
    return Response.json({ title: "Caminho inválido", status: 400 }, { status: 400 });
  }
  const target = new URL(`/api/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`, BACKEND_URL);

  const headers = new Headers({ accept: "application/json" });
  const contentType = request.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  const secret = process.env.FRONTEND_SHARED_SECRET;
  if (secret) headers.set("x-frontend-secret", secret);
  const visitor = visitorAddress(request);
  if (visitor) headers.set("x-client-ip", visitor);

  let response: Response;
  try {
    response = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
      cache: "no-store",
      redirect: "manual",
    });
  } catch {
    return Response.json(
      { title: "Servidor indisponível", status: 502, detail: "Não conseguimos falar com o servidor. Tente de novo em instantes." },
      { status: 502 },
    );
  }

  const out = new Headers();
  for (const name of RESPONSE_HEADERS) {
    const value = response.headers.get(name);
    if (value) out.set(name, value);
  }
  return new Response(response.body, { status: response.status, headers: out });
}

export { relay as GET, relay as POST, relay as PUT, relay as PATCH, relay as DELETE };
