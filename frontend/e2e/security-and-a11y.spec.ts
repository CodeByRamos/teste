import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test("pages carry a nonce-based Content-Security-Policy and hardening headers", async ({ request }) => {
  const response = await request.get("/");
  const headers = response.headers();
  const policy = headers["content-security-policy"] ?? "";
  expect(policy).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+' 'strict-dynamic'/);
  expect(policy).toContain("object-src 'none'");
  expect(policy).toContain("frame-ancestors 'none'");
  expect(headers["x-content-type-options"]).toBe("nosniff");
  expect(headers["x-frame-options"]).toBe("DENY");

  // A fresh nonce on every request.
  const again = (await request.get("/")).headers()["content-security-policy"];
  expect(again).not.toBe(policy);
});

test("the shared API secret never reaches the browser", async ({ page }) => {
  const seen: string[] = [];
  page.on("request", (request) => seen.push(JSON.stringify(request.headers())));
  await page.goto("/montar/resultado?orcamento=30000&usos=GAMING_AAA");
  await expect(page.getByRole("heading", { name: "Compatibilidade" })).toBeVisible();
  expect(seen.join("\n")).not.toMatch(/x-frontend-secret/i);
});

for (const path of ["/", "/montar", "/verificar", "/melhorar", "/creditos", "/montar/resultado?orcamento=30000&usos=GAMING_AAA"]) {
  test(`no WCAG A/AA violations on ${path}`, async ({ page }) => {
    await page.goto(path);
    if (path.includes("resultado")) {
      await expect(page.getByRole("heading", { name: "E se eu mudar o orçamento?" })).toBeVisible();
      await expect(page.getByLabel("Calculando alternativas")).toHaveCount(0);
    }
    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze();
    const summary = results.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(" ")).join(", ")}`);
    expect(summary).toEqual([]);
  });
}
