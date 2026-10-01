# Bot de preços (verificação e comparação nos sites das lojas)

Complementa os feeds de parceiros ([`PRICING.md`](PRICING.md)): um robô próprio visita as páginas de produto das
lojas, lê o preço **publicado pela própria loja** e alimenta a comparação entre lojas. Nada é inventado: sem página
lida com sucesso, não há preço.

## Situação das lojas (verificado em out/2026)

| Loja | robots.txt | Sitemap | Dados estruturados na página | Situação |
|---|---|---|---|---|
| KaBuM! | produtos permitidos | `sitemap.xml` → `sitemap/hardware.xml` (~1.000 produtos) | preço, estoque e marca; **sem EAN**, código do fabricante no nome | **funciona** (testado) |
| Terabyte | produtos permitidos | `hardware/sitemap.xml` (~7.400) | preço, estoque, **EAN e código do fabricante** | **bloqueia robôs** (Cloudflare responde 403) |
| Pichau | permite | `media/sitemap.xml` (~29 mil) | — | **bloqueia robôs** (403) |

Quando uma loja bloqueia o robô (403/429 ou proteção anti-bot), o bot **para e respeita**. Não tentamos contornar
proteções. Para essas lojas o caminho é feed de afiliado ou acordo com a loja.

## Como funciona

1. `robots.txt` da loja (RFC 9309: grupo do nosso robô ou `*`, regra mais específica vence, `Crawl-delay`).
2. Sitemap → só URLs de produto da própria loja que o `robots.txt` permite (filtro de sitemap e de URL configuráveis).
3. Página de produto → dados estruturados JSON-LD schema.org `Product`/`Offer` (não depende do visual da página).
4. Casamento com o catálogo (`CatalogMatcher`), do mais forte ao mais fraco: EAN; código do fabricante + marca;
   código do fabricante **no título** + marca. Trava de categoria: se o título diz o tipo do produto ("Ventoinha",
   "Placa de Vídeo"…) e ele não bate com a peça, não há casamento. No nível mais fraco o tipo tem de ser reconhecido.
   (Essa trava nasceu de um caso real: o registro de um water cooler no OpenDB trazia o código do kit de ventoinhas.)
5. Validação antimanipulação (`OfferValidation`): HTTPS no domínio da loja, faixa plausível, comparação com outras
   lojas e com o preço anterior (queda brusca fica retida).
6. Oferta (`store_offer`) e **histórico** (`price_observation`).

Revisita: produto casado a cada 6 h; produto que não vendemos a cada 7 dias; página que não é produto a cada 30 dias.
Páginas sem mudança não são baixadas de novo (`ETag`/`Last-Modified`).

## Conduta

- Identifica-se: `PCPriceBot/1.0 (+contato)`; sem contato configurado o bot não liga.
- Uma requisição por vez por loja, intervalo mínimo de 5 s (ou o `Crawl-delay`, se maior).
- 403/429/503 pausam a loja (1 h; 24 h se repetir); `robots.txt` com 401/403 = loja fechada para robôs (24 h).
- Sem login, sem formulário, sem dado pessoal. Só páginas públicas de produto.

## Ligar uma loja (variáveis de ambiente na API)

```
PRICE_BOT_CONTACT=https://seu-site-ou-email-de-contato
PLATFORM_PRICING_CRAWLERS_0_ID=kabum-bot
PLATFORM_PRICING_CRAWLERS_0_STORE=KaBuM!
PLATFORM_PRICING_CRAWLERS_0_SITEMAPS=https://www.kabum.com.br/sitemap.xml
PLATFORM_PRICING_CRAWLERS_0_SITEMAPFILTER=hardware
PLATFORM_PRICING_CRAWLERS_0_PRODUCTPATTERN=kabum\.com\.br/produto/
PLATFORM_PRICING_CRAWLERS_0_ALLOWEDDOMAINS=kabum.com.br
PLATFORM_PRICING_CRAWLERS_0_ENABLED=true
```

Antes de ligar uma loja, confira os termos de uso do site: algumas proíbem acesso automatizado nos termos mesmo que o
`robots.txt` permita. Acompanhe com `GET /admin/price-bot` (cabeçalho `X-Admin-Token`): páginas por situação,
sitemaps lidos, ofertas aceitas/retidas, pausa e último problema.

## Para o frontend (comparação)

Cada item de `POST /api/recommendations` e `POST /api/builds/evaluate` traz:

| Campo | O que é |
|---|---|
| `price` | o preço usado no total (menor oferta com estoque) |
| `offers` | **todas** as lojas com preço para a peça, do menor para o maior: `amountBrl`, `storeName`, `url`, `observedAt`, `kind` (`REAL`/`EXAMPLE`), `isExample` |
| `lowest30Days` | menor preço visto nos últimos 30 dias (`amountBrl`, `storeName`, `observedAt`), ou `null` sem histórico |

Sugestões de tela: lista de lojas por peça com destaque para a mais barata, "visto há X horas", link "Ver na loja"
(`rel="sponsored nofollow noopener"`), e "menor preço em 30 dias: R$ X na loja Y" quando o preço atual estiver acima.
Preços de exemplo (`isExample`) devem continuar rotulados como fictícios.
