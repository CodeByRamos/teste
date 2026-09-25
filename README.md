# Configurador de PC (nome provisório)

Plataforma que transforma necessidades ("quero jogar e programar, tenho R$ 5.000") em uma configuração de PC
compatível, justificada e fácil de entender.

> O nome e a identidade visual ainda não foram definidos. O nome exibido vem de `NEXT_PUBLIC_APP_NAME`
> e as cores de `frontend/src/app/globals.css`, então trocar a marca significa mudar esses dois lugares.

## Estrutura

```
backend/    API em Java 25 + Spring Boot 4 (motores, adapter do OpenDB, PostgreSQL)
3d/         gerador da biblioteca de modelos 3D paramétricos (GLB)
frontend/   Web em Next.js 16 + React 19 + Tailwind 4 (só apresentação)
scripts/    fetch-opendb.sh — baixa uma versão fixa do BuildCores OpenDB
data/       snapshots do OpenDB (não versionados)
docs/       arquitetura e decisões
```

## Rodando localmente

Pré-requisitos: JDK 25, Node 20.9+, Git Bash (no Windows).

```bash
# 1. Baixar a versão fixada do OpenDB (só as 8 categorias de PC, ~85 MB)
scripts/fetch-opendb.sh

# 2. Backend: sobe um PostgreSQL 17 embutido (sem Docker), aplica migrações,
#    importa o OpenDB na primeira execução (~2 min) e abre a API em :8080
cd backend && ./mvnw spring-boot:test-run

# 3. Frontend em :3000 (encaminha /api/* para o backend)
cd frontend && npm install && npm run dev
```

Testes: `cd backend && ./mvnw test` (56 testes, incluindo ponta a ponta com PostgreSQL real),
`cd 3d && node src/validate.ts` (modelos 3D)
e `cd frontend && npm run lint && npx tsc --noEmit`.

## O que já funciona

- **Montar meu PC**: texto livre ou assistente (usos, prioridade, resolução, orçamento, peças que já tem)
  → configuração completa com explicação em camadas, especificações, alternativas e troca de peças.
- **Motor de compatibilidade**: 11 pares verificados por regras, com três resultados (compatível, atenção,
  incompatível) e explicação simples. Dados ausentes viram "atenção — não conseguimos confirmar", nunca suposição.
- **Tenho um PC**: verificação de compatibilidade das peças informadas.
- **Quero melhorar meu PC**: diagnóstico do PC atual (bom / suficiente / fraco / gargalo) e o upgrade de maior ganho
  dentro do orçamento, com tudo o que precisa mudar junto (fonte, gabinete, placa-mãe, memória, cooler) e o que continua.
- **Visualização 3D**: cada configuração pode ser vista em 3D, montada com modelos paramétricos adaptados às medidas
  reais de cada peça; clicar numa peça abre a explicação dela. Cena de teste com verificação de colisões em
  `/laboratorio-3d`. Biblioteca e pipeline em [`3d/`](3d/README.md).
- **Salvar configurações**: link permanente com o que foi mostrado no momento.
- **Créditos**: atribuição ODC-By, versão dos dados e completude por categoria.

## Limitações conhecidas (declaradas também na interface)

| Tema | Situação |
|---|---|
| Preços | **Fictícios** (`ExamplePriceProvider`), rotulados em toda a interface. Provedores reais (lojas brasileiras) são o próximo passo. |
| Desempenho | Estimativa a partir de especificações (núcleos, frequência, cache) com calibração aproximada por arquitetura de GPU; não é benchmark. Os ganhos aparecem arredondados. |
| Requisitos de jogos | Sem base de requisitos por jogo; usamos perfis de uso (competitivo / pesado + resolução). |
| BIOS | Sem dados de BIOS no OpenDB; casos conhecidos (ex.: Ryzen 5000 em B450) geram "atenção". |
| Contas de usuário | Ainda não há login; configurações salvas são acessadas pelo link (ID aleatório). |
| 3D | Biblioteca própria de 30 modelos paramétricos (~98% dos componentes); medidas ausentes nos dados viram valores típicos, avisados. Ver [`docs/3d/MODEL_LIBRARY.md`](docs/3d/MODEL_LIBRARY.md). |

## Dados e licença

As especificações técnicas vêm do [BuildCores OpenDB](https://github.com/buildcores/buildcores-open-db),
disponibilizado sob a [Open Data Commons Attribution License (ODC-By) v1.0](https://opendatacommons.org/licenses/by/1-0/).
Veja [`licenses/opendb/NOTICE.md`](licenses/opendb/NOTICE.md) e [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Deploy

O frontend e o backend sobem separados.

**Backend (Java + PostgreSQL)** — qualquer serviço que rode container: AWS App Runner/ECS (planejado), Railway, Render, Fly.

```bash
docker build -f backend/Dockerfile -t platform-api .   # a partir da raiz do repositório
```

| Variável | Exemplo |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://host:5432/db` (formato JDBC) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | credenciais do banco (RDS: Secrets Manager) |
| `PORT` | definido pela plataforma (padrão 8080) |
| `EXAMPLE_PRICES` | `true` até existir um provedor real de preços |
| `TRUSTED_PROXIES` | IP do frontend, se o rate limit deve usar o IP real do usuário |

A imagem já contém o snapshot fixado do OpenDB. A primeira inicialização importa os dados (alguns minutos);
as seguintes pulam a importação. Health check: `GET /actuator/health`.

**Frontend (Vercel)** — importar o repositório, *Root Directory* = `frontend`, variável `BACKEND_URL` com a URL
pública do backend (usada no build para o proxy `/api/*`). Opcional: `NEXT_PUBLIC_APP_NAME`.
