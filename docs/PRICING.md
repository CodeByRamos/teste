# Preços reais: feeds de lojas

Preços vêm dos **catálogos de produtos que as lojas publicam para parceiros** (feeds de afiliados). Não fazemos
scraping de sites, e nenhum preço é inventado: sem oferta válida, a peça fica com o preço de exemplo, sempre rotulado
como fictício (ou sem preço, se `EXAMPLE_PRICES=false`).

## Fontes viáveis no Brasil (pesquisa de set/2026)

| Fonte | Como funciona | Observações |
|---|---|---|
| **KaBuM! via Awin** | Programa de afiliados na Awin; a Awin fornece feed de produtos (CSV) com EAN, preço, estoque e link de afiliado | Melhor encaixe para hardware. Exige cadastro e aprovação na Awin. |
| **Lomadee** | Rede brasileira com 300+ lojas e API de ofertas | A API não permite carga em massa; bom como complemento. |
| **Amazon (Creators API)** | Substituiu a PA-API 5.0 (desativada em maio/2026); cobre amazon.com.br | Exige conta de Associados com vendas qualificadas. |
| Mercado Livre | Busca pública por API | Marketplace com anúncios de terceiros: casamento com o catálogo pouco confiável. Não recomendado agora. |

Todas exigem uma conta de parceiro, que precisa ser criada por uma pessoa (não automatizável).

## Como ligar um feed

Cada loja é configurada por variáveis de ambiente no serviço da API (índice `0`, `1`, … por loja; nomes no formato do
Spring: pontos viram `_` e hífens somem):

| Variável | Exemplo | |
|---|---|---|
| `PLATFORM_PRICING_FEEDS_0_ID` | `kabum` | identificador estável, minúsculo |
| `PLATFORM_PRICING_FEEDS_0_STORE` | `KaBuM!` | nome mostrado às pessoas |
| `PLATFORM_PRICING_FEEDS_0_FORMAT` | `awin` | `awin` ou `generic` |
| `PLATFORM_PRICING_FEEDS_0_URL` | `https://productdata.awin.com/…` | link de download do feed; **é segredo** (contém a chave da API) |
| `PLATFORM_PRICING_FEEDS_0_ALLOWEDDOMAINS` | `kabum.com.br,awin1.com` | domínios que os links das ofertas podem usar |

Com URL configurada, o feed é baixado ao iniciar e a cada 6 horas (`PLATFORM_PRICING_FEEDREFRESH`, ex.: `PT6H`).
Sem URL, dá para enviar o arquivo manualmente (CSV, pode ser `.gz`), se `ADMIN_TOKEN` estiver definido:

```bash
curl -X POST -H "X-Admin-Token: $ADMIN_TOKEN" --data-binary @feed.csv.gz https://SUA-API/admin/price-feeds/kabum
```

A resposta resume o import: linhas lidas, casadas com o catálogo, aceitas e o motivo de cada rejeição.

**Colunas.** O formato `awin` usa `ean`, `mpn`, `brand_name`, `search_price`, `aw_deep_link`, `in_stock`,
`last_updated`, `currency`. As colunas do feed da Awin são escolhidas pelo parceiro no painel: confira o cabeçalho
do arquivo e ajuste o que for diferente, por exemplo `PLATFORM_PRICING_FEEDS_0_COLUMNS_GTIN=product_GTIN`. O
formato `generic` usa `gtin, mpn, brand, price_brl, url, availability, observed_at, currency`.

## Como as ofertas são casadas com o catálogo

1. **Código de barras** (EAN/UPC/GTIN), normalizado para 14 dígitos e com dígito verificador conferido.
2. Sem código de barras: **MPN idêntico**, único no catálogo, **e** a marca do feed igual ao fabricante da peça.
   Sem coluna de marca, não há casamento por MPN.

Cobertura de código de barras no OpenDB (snapshot atual): CPU 85%, armazenamento 55%, RAM 50%, GPU 49%,
placa-mãe 40%, fonte 32%. O MPN existe em ~100% dos registros, por isso o segundo nível.

## Regras que protegem contra preço errado ou manipulado

Uma oferta só é aceita se passar em todas:

- preço entre R$ 10 e R$ 100.000, em BRL; valores ambíguos (`1.234`) são descartados, nunca adivinhados;
- link **HTTPS** para um domínio permitido daquela loja (sem truques como `loja.com.br@outro-site`);
- data não está no futuro nem tem mais de 72 horas;
- não fica abaixo de 40% nem acima de 250% da mediana das outras lojas (com pelo menos 2 outras);
- não cai mais de 60% em relação ao preço anterior da mesma loja (fica retida como suspeita).

Se um feed vier quebrado e o número de ofertas válidas da loja despencar (menos de 20% do anterior), **o import é
abortado** e as ofertas anteriores continuam. Ofertas com mais de 72 horas deixam de aparecer sozinhas.

## Na interface

- Preço real mostra a loja, "preço visto há X horas" e o link **Ver na loja** (`rel="sponsored nofollow"`), com aviso
  de que alguns links são de parceiros.
- Quando o total mistura preços reais e de exemplo, o rótulo diz "inclui preços fictícios".
- Entre variantes do mesmo chip de GPU, uma com preço real vence uma com preço fictício.
- A página de créditos lista as lojas, quantas peças cada uma cobre e quando foi atualizada.
