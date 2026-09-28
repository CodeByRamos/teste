import { expect, test, type Page } from "@playwright/test";

/** The page never scrolls sideways (the layout fits phones down to 360 px). */
async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(1);
}

test("wizard builds a compatible PC, explains it and saves it", async ({ page }) => {
  await page.goto("/montar");

  await page.getByText("Jogos pesados", { exact: true }).click();
  await page.getByRole("button", { name: "Continuar" }).click();

  await page.getByText("Quad HD (1440p)").click();
  await page.getByRole("button", { name: "Continuar" }).click();

  await page.getByRole("spinbutton", { name: "Orçamento" }).fill("30000");
  await page.getByRole("button", { name: "Continuar" }).click();

  await page.getByRole("button", { name: "Não, vou comprar tudo" }).click();
  await page.getByRole("button", { name: "Montar meu PC" }).click();

  await expect(page).toHaveURL(/\/montar\/resultado\?orcamento=30000/);
  const parts = page.locator("section[aria-labelledby=pecas-title] > ul > li");
  await expect(parts.first()).toBeVisible();
  expect(await parts.count()).toBeGreaterThanOrEqual(7);
  await expect(page.getByRole("heading", { name: "Compatibilidade" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Pensando no futuro" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "E se eu mudar o orçamento?" })).toBeVisible();
  // Fictitious prices are always labeled while no store feed is connected.
  await expect(page.getByText("Total estimado (preços fictícios)")).toBeVisible();
  await expectNoHorizontalScroll(page);

  // Plain-language explanation opens per part.
  await parts.first().getByRole("button").first().click();
  await expect(page.getByText("O que é").first()).toBeVisible();

  await page.getByRole("button", { name: "Salvar configuração" }).click();
  await expect(page).toHaveURL(/\/configuracao\/[0-9a-f-]{36}$/);
  await expect(page.getByRole("heading", { name: "Compatibilidade" })).toBeVisible();

  await page.goto("/minhas-configuracoes");
  await expect(page.getByRole("link", { name: /Jogos pesados/ }).first()).toBeVisible();
});

test("free text goes straight to a build when nothing is missing", async ({ page }) => {
  await page.goto("/");
  await page.getByLabel("Descreva o computador que você quer").fill("Quero um PC de 30 mil para jogar jogos pesados em 1440p");
  await page.getByLabel("Descreva o computador que você quer").press("Enter");

  await expect(page.getByText("Orçamento de até R$ 30.000")).toBeVisible();
  await page.getByRole("button", { name: "Ver meu PC" }).click();

  await expect(page).toHaveURL(/orcamento=30000/);
  await expect(page.getByRole("heading", { name: "Compatibilidade" })).toBeVisible();
});

test("parts checker finds parts by name", async ({ page }) => {
  await page.goto("/verificar");
  await page.getByPlaceholder(/./).first().fill("ryzen");
  const results = page.getByRole("region", { name: "Resultados da busca" }).getByRole("button");
  await expect(results.first()).toBeVisible();
  await results.first().click();
  await expect(page.getByText("Suas peças (1)")).toBeVisible();
  await expectNoHorizontalScroll(page);
});

test("unknown pages get a helpful 404", async ({ page }) => {
  const response = await page.goto("/pagina-que-nao-existe");
  expect(response?.status()).toBe(404);
  await expect(page.getByRole("heading", { name: "Não encontramos esta página" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Montar um PC" })).toBeVisible();
});
