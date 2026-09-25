# Deploy: backend no Railway, frontend no Vercel

```
navegador ──► Vercel (Next.js) ──/api/* + segredo──► Railway (API Java) ──► Railway (PostgreSQL)
```

O navegador só fala com o site no Vercel. O Next.js repassa `/api/*` para a API com um **segredo compartilhado**
(`FRONTEND_SHARED_SECRET`); com ele configurado, a API recusa (403) qualquer chamada que não venha do site.

## 1. Gerar o segredo compartilhado

Uma sequência aleatória longa. Guarde-a só nas variáveis de ambiente dos dois serviços (nunca no código):

```bash
openssl rand -base64 48
```

## 2. Railway: banco + API

1. Em [railway.com](https://railway.com), **New Project → Deploy from GitHub repo** → escolha este repositório.
   O Railway lê o [`railway.json`](../railway.json) da raiz: build pelo `backend/Dockerfile`, health check em
   `/actuator/health/readiness` e reinício automático em falha.
2. No mesmo projeto: **+ New → Database → PostgreSQL**.
3. No serviço da API, aba **Variables**, adicione:

   | Variável | Valor |
   |---|---|
   | `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` (referência ao banco; o formato `postgresql://` é convertido automaticamente) |
   | `FRONTEND_SHARED_SECRET` | o segredo do passo 1 |
   | `EXAMPLE_PRICES` | `true` (preços fictícios, rotulados, até existirem provedores reais) |

4. Aba **Settings → Networking → Generate Domain** para ter a URL pública (ex.: `https://api-xxxx.up.railway.app`).

**Primeira subida:** a imagem já traz o snapshot fixado do OpenDB e o importa ao iniciar. A API só é marcada como
pronta (e só recebe tráfego) depois que o catálogo está carregado; o health check espera até 20 minutos. As subidas
seguintes pulam a importação quando o snapshot não mudou.

Conferência rápida (deve responder `{"status":"UP"}`):

```bash
curl https://SUA-API.up.railway.app/actuator/health/readiness
```

E `curl https://SUA-API.up.railway.app/api/options` deve responder **403**: a API só atende o site.

## 3. Vercel: frontend

1. [vercel.com](https://vercel.com) → **Add New → Project** → importe este repositório.
2. **Root Directory:** `frontend` (o framework Next.js é detectado sozinho).
3. **Environment Variables:**

   | Variável | Valor |
   |---|---|
   | `BACKEND_URL` | a URL pública da API no Railway, sem barra no final |
   | `FRONTEND_SHARED_SECRET` | o mesmo segredo do passo 1 |
   | `NEXT_PUBLIC_APP_NAME` | nome exibido no site (opcional; a marca ainda não foi definida) |

4. **Deploy.** Cada push na `main` publica de novo o site (Vercel) e a API (Railway).

## Custos e limites

- Railway cobra por uso (CPU/memória/tráfego); a API usa até 75% da memória do serviço (`-XX:MaxRAMPercentage=75`).
- O limite de requisições (60 POST/min por visitante) fica em memória: correto para **uma** instância da API. Para
  várias instâncias, o limite deve ir para a borda (gateway/WAF).
- Trocar de provedor depois (ex.: AWS App Runner + RDS) usa a mesma imagem Docker e as mesmas variáveis;
  `DATABASE_URL` também aceita o formato `jdbc:postgresql://` com `DATABASE_USERNAME`/`DATABASE_PASSWORD`.

## Segurança

- Segredos só nas variáveis de ambiente dos provedores; nada no repositório nem no navegador.
- O navegador nunca recebe o segredo: ele é adicionado pelo servidor do Next.js (`src/app/api/[...path]/route.ts`).
- O IP do visitante usado no limite de requisições só é aceito quando acompanhado do segredo.
- Para trocar o segredo: gere outro, atualize nos dois serviços e publique de novo.
