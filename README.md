# Configurador de PC (nome provisório)

Plataforma que transforma necessidades ("quero jogar e programar, tenho R$ 5.000") em uma configuração de PC
compatível, justificada e fácil de entender.

> O nome e a identidade visual ainda não foram definidos. O nome exibido vem de `NEXT_PUBLIC_APP_NAME`
> e as cores de `frontend/src/app/globals.css`, então trocar a marca significa mudar esses dois lugares.

## Estrutura

```
backend/    API em Java 25 + Spring Boot 4 (motores, adapter do OpenDB, PostgreSQL)
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

Testes: `cd backend && ./mvnw test` (45 testes, incluindo ponta a ponta com PostgreSQL real)
e `cd frontend && npm run lint && npx tsc --noEmit`.

## O que já funciona

- **Montar meu PC**: texto livre ou assistente (usos, prioridade, resolução, orçamento, peças que já tem)
  → configuração completa com explicação em camadas, especificações, alternativas e troca de peças.
- **Motor de compatibilidade**: 11 pares verificados por regras, com três resultados (compatível, atenção,
  incompatível) e explicação simples. Dados ausentes viram "atenção — não conseguimos confirmar", nunca suposição.
- **Tenho um PC**: verificação de compatibilidade das peças informadas.
- **Salvar configurações**: link permanente com o que foi mostrado no momento.
- **Créditos**: atribuição ODC-By, versão dos dados e completude por categoria.

## Limitações conhecidas (declaradas também na interface)

| Tema | Situação |
|---|---|
| Preços | **Fictícios** (`ExamplePriceProvider`), rotulados em toda a interface. Provedores reais (lojas brasileiras) são o próximo passo. |
| Desempenho | Estimativa a partir de especificações (núcleos, frequência, cache), não benchmark. Tende a subestimar GPUs AMD frente às NVIDIA. |
| Requisitos de jogos | Sem base de requisitos por jogo; usamos perfis de uso (competitivo / pesado + resolução). |
| BIOS | Sem dados de BIOS no OpenDB; casos conhecidos (ex.: Ryzen 5000 em B450) geram "atenção". |
| Contas de usuário | Ainda não há login; configurações salvas são acessadas pelo link (ID aleatório). |
| 3D | Diagrama esquemático interativo. O renderer da BuildCores depende de API paga e não permite clique por peça (ver docs). |

## Dados e licença

As especificações técnicas vêm do [BuildCores OpenDB](https://github.com/buildcores/buildcores-open-db),
disponibilizado sob a [Open Data Commons Attribution License (ODC-By) v1.0](https://opendatacommons.org/licenses/by/1-0/).
Veja [`licenses/opendb/NOTICE.md`](licenses/opendb/NOTICE.md) e [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Produção (AWS) — planejado

- Backend: container (ECS Fargate ou App Runner) com `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
  apontando para **Amazon RDS for PostgreSQL 17**; segredos no Secrets Manager.
- Importação do OpenDB como job separado (`OPENDB_SNAPSHOT_DIR` + `OPENDB_INGEST_ON_STARTUP=true`).
- `EXAMPLE_PRICES=false` assim que houver um provedor real de preços.
- Frontend: `BACKEND_URL` apontando para a API; `TRUSTED_PROXIES` no backend com o endereço do frontend.
