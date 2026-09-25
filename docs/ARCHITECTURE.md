# Arquitetura

## Visão geral

```text
Next.js (apresentação)  ──/api/*──▶  Spring Boot (Java 25)
                                         │
             ┌───────────────────────────┼─────────────────────────────┐
             ▼                           ▼                             ▼
   intake (texto livre)      recommendation (motor)          compatibility (regras)
             │                           │                             │
             └──────────────▶  hardware (modelo de domínio) ◀──────────┘
                                         ▲
                     catalog (em memória) │         pricing (PriceProvider)
                                         │                  ▲
                       infra.opendb (adapter)     infra.pricing (ExamplePriceProvider)
                                         │
                                  PostgreSQL (RDS)
```

Regras verificadas por teste de arquitetura (`ArchitectureTest`):

- Os pacotes de domínio (`hardware`, `compatibility`, `recommendation`, `pricing`, `catalog`, `intake`, `builds`)
  não dependem de Spring, Jackson, JDBC nem de `infra`/`api`.
- Só o adapter `infra.opendb` conhece o formato dos JSONs do OpenDB.
- O frontend não contém regra de negócio: recebe documentos prontos da API.

## Decisões

### D1 — Modelo híbrido para os dados do OpenDB

Medido no commit `399c716`: 48,3 mil registros (135 MB), dos quais ~26,2 mil nas 8 categorias de PC.

- *Consumir direto* foi descartado: não há API pública de leitura, e arquivos soltos não permitem busca nem filtro.
- *Só referências* não basta: o motor filtra milhares de candidatos por encaixe, consumo e dimensões.
- **Adotado:** snapshot fixado por commit → ingestão idempotente no PostgreSQL com
  `specs` (projeção normalizada), `raw` (registro original intacto), `quality_*`, identificadores EAN/UPC/MPN
  e `status` (`removed_upstream` em vez de apagar). O catálogo ativo é carregado em memória para o motor.
- IDs externos preservados (`source`, `external_id`); o ID interno é derivado deterministicamente deles.

### D2 — Nunca confiar cegamente na base externa

Cada campo passa por validação de faixa plausível; valores implausíveis são descartados e registrados
(`DataIssue`). Aliases de encaixe são normalizados (`sTR4`→`TR4`). Campos críticos ausentes reduzem a nota de
qualidade e, na compatibilidade, geram "atenção — não conseguimos confirmar".

### D3 — Compatibilidade determinística, com explicação

`Fit` concentra as verificações par a par (resultado SIM/NÃO/DESCONHECIDO). As regras (`*Rule`) usam `Fit` e
explicam o resultado em linguagem simples; o motor de recomendação usa o mesmo `Fit` para só escolher peças
que passam. Regras conhecidas que o nome do encaixe não captura (LGA 1151 em duas gerações, BIOS em AM4/AM5/LGA 1700)
ficam em `PlatformSupport`.

### D4 — Recomendação por busca, não por palpite

Para cada par CPU × GPU elegível (GPU no nível do chip, variante mais barata), o motor completa a configuração com
as peças mais baratas que atendem aos requisitos e à compatibilidade, e escolhe a de maior utilidade ponderada
dentro do orçamento. A utilidade usa escala logarítmica por categoria, penaliza desequilíbrio CPU/GPU em jogos e
tem um teto de "desempenho suficiente" por uso (estudo não precisa de processador topo de linha).
As regras de produto (tabela de usos) ficam em `RequirementAnalyzer`.

### D5 — Preços separados das especificações

`PriceProvider` é uma porta; o único provedor atual é `ExamplePriceProvider` (fictício, sempre rotulado,
sem URL). Preços são sempre resolvidos no servidor — salvar uma configuração recalcula tudo a partir dos IDs.

### D6 — 3D como camada opcional

`@buildcores/render-client` (ISC) é um cliente de uma API de parceiros paga (`renderapi.buildcores.com`) que devolve
vídeo/sprite 360°, usa IDs próprios (não o `opendb_id`) e não expõe clique por peça. Por isso o produto usa um
diagrama esquemático interativo, que funciona para 100% das configurações; o 3D será avaliado em prova de conceito
depois de conversar com a BuildCores (preço, mapeamento de IDs, cobertura de modelos, interação).

### D7 — Upgrades sempre com dependências

`UpgradeAdvisor` avalia o PC atual (bom / suficiente / fraco / gargalo) e gera planos por tipo: placa de vídeo,
processador no mesmo encaixe, plataforma (processador + placa-mãe + memória se o tipo mudar), memória e SSD.
Cada plano verifica o que a troca força — fonte (potência e conectores), gabinete (comprimento), cooler (encaixe e
altura), discos que precisam continuar conectados — e o PC resultante passa pelo motor de compatibilidade; um plano
só é descartado se criar uma incompatibilidade nova. A recomendação compara combinações (troca principal com espaço
reservado para correções baratas de memória e SSD) e escolhe o maior ganho dentro do orçamento, mesclando compras da
mesma peça.

### D8 — Reimportação incremental e versionada

A ingestão grava a versão do mapeador junto do commit (`source_snapshot.mapper_version`): mudar uma regra de
validação reimporta o mesmo commit. A carga usa tabela de staging e reescreve só registros que mudaram. Conjuntos
no modelo de domínio são ordenados, então o JSON gravado é determinístico entre execuções.

### D9 — Calibração de desempenho de GPU

Núcleos × frequência superestima placas NVIDIA a partir da série RTX 30 (núcleos FP32 contados em dobro). Um fator
por arquitetura (`PerformanceEstimator`) aproxima as gerações; ganhos são exibidos arredondados ("cerca de 2,5×").
Continua sendo estimativa até existir a tabela de benchmarks com fonte.

## Segurança

- Validação de entrada com Bean Validation; erros em ProblemDetail sem detalhes internos.
- Preços nunca aceitos do cliente; IDs de configurações salvas são UUID aleatórios.
- Rate limit por cliente nas rotas POST; `X-Forwarded-For` só é aceito de proxies confiáveis configurados.
- Segredos só por variáveis de ambiente; nenhuma credencial no frontend (o navegador fala apenas com o Next.js).
- Cabeçalhos de segurança no frontend (`nosniff`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`).
- Textos vindos da base comunitária são tratados como dados, nunca como instruções (relevante para a futura IA).

## Próximos passos

1. Provedor de preços real (lojas brasileiras / afiliados), casando produtos por EAN/GTIN.
2. Tabela curada de desempenho (benchmarks com fonte citada) no lugar da estimativa por especificação.
3. Autenticação (Amazon Cognito) e configurações por usuário.
4. Planejamento futuro ("o que comprar agora pensando nos próximos anos") sobre o `UpgradeAdvisor`.
5. Camada de IA usando os motores como ferramentas (interpretar e explicar, nunca decidir compatibilidade).
6. Prova de conceito do renderer 3D.
