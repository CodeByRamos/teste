# Biblioteca de modelos 3D paramétricos

Objetivo: **representar milhares de componentes reais com poucos modelos**. Um modelo base por família
(ex.: `gpu-dual-fan`) é adaptado em tempo de execução às medidas de cada componente. Não existe modelo por SKU.

Estado atual: **30 modelos base gerados** representam **~98% dos componentes** das 7 categorias desenhadas
(~24,9 mil registros do OpenDB), com GLBs de 6 a 75 KB cada.

---

## 1. Análise do OpenDB

Script: [`3d/src/analyze-opendb.ts`](../../3d/src/analyze-opendb.ts). Dados brutos: [`opendb-analysis.json`](opendb-analysis.json).
Snapshot `399c716` (8 categorias de PC + `CaseFan` de um checkout completo).

O OpenDB não tem popularidade ou vendas. Usamos dois indicadores, sem misturá-los com fatos:
- **anunciado:** número de listagens em lojas (produto vendido por muitas lojas é o que as pessoas compram);
- **atual:** lançado em 2020 ou depois, quando o ano é informado.

| Categoria | Registros | Achado que define as famílias |
|---|---|---|
| GPU | 3.862 | Campo `cooling` preenchido em 100%: **3 fans 44%**, **2 fans 38%**, **1 fan 14%**, híbrida/água 2%, passiva 1%, blower 0,5%. Comprimento mediano por família: 184 / 245 / 308 mm. Espessura: 2 slots 51%, 3 slots 31%, 2,5 slots 8%, 4 slots 6%. 608 placas com conector de 16 pinos. |
| Gabinete | 3.783 | ATX mid tower **62%**, micro-ATX 17%, full tower 9%, Mini-ITX 9%. Medidas externas em 33% dos registros; medianas por família usadas como padrão (mid tower 235 × 481 × 465 mm). Lateral de vidro em 83%. |
| Placa-mãe | 3.701 | ATX 46%, micro-ATX 41%, Mini-ITX 8%, E-ATX e servidores 5%. Slots de memória: 4 (ATX), 2 ou 4 (mATX), 2 (ITX). |
| Cooler | 2.405 | **53% são AIO** (360 mm: 519; 240 mm: 455; 280 mm: 112; 120 mm: 123; 420 mm: 49). Air: torre 150–169 mm é o grupo principal; 231 perfil baixo (< 80 mm). |
| RAM | 4.876 | Com dissipador 45%, RGB 35%, sem dissipador 17%, SO-DIMM 3% (notebook, fora do escopo). |
| Fonte | 3.297 | ATX **93%**, SFX 5%, outros formatos (TFX, Flex) 1%. |
| Armazenamento | 3.495 | M.2 46%, HD 3,5" 24%, SSD SATA 2,5" 24%, HD 2,5" 5%. |
| Fans (CaseFan) | 3.465 | 120 mm **61%**, 140 mm 24%, 80/92 mm 9%. Iluminação ARGB/RGB em 40%. |

## 2. Ranking das famílias (o que modelar primeiro)

A ordem combina três critérios:
- **cobertura:** quantos componentes um modelo representa;
- **presença:** quantos PCs usam a peça;
- **peso visual:** quanto a peça define como o PC parece.

A lista pedida (GPU → gabinete → placa-mãe → cooler → RAM → fonte → fans → SSD → HD → AIO) foi mantida, com um ajuste:
**o AIO subiu para junto do air cooler**, porque é metade dos coolers da base e o radiador é uma das peças mais visíveis.

1. **GPU (dual e triple fan):** 82% das placas, e é a peça que mais aparece.
2. **Gabinete ATX mid tower:** o "container" de 62% dos gabinetes; define a cena inteira.
3. **Placa-mãe ATX e micro-ATX:** 87% das placas; é a base de montagem de CPU, RAM, GPU e M.2.
4. **Cooler torre e radiadores 240/360:** juntos, cerca de 75% dos coolers.
5. **RAM com dissipador e RGB:** 80% dos kits; é uma peça pequena, mas sempre à vista.
6. **Fonte ATX:** 93%; fica quase sempre escondida no shroud, o que justifica pouco detalhe.
7. **Fans de 120 e 140 mm:** 85%; aparecem no gabinete e nos radiadores.
8. **SSD M.2:** 46% do armazenamento, montado na placa-mãe.
9. **HD 3,5" e 2,5":** geometria trivial; ficam nas baias.
10. **Variantes de cauda:** GPU single-fan, gabinetes full tower e ITX, cooler de perfil baixo, SFX.

## 3. Catálogo inicial

Arquivo gerado: [`frontend/public/3d-models/catalog.json`](../../frontend/public/3d-models/catalog.json)
(inclui cobertura, triângulos, tamanho, partes e pontos de montagem de cada modelo).

| Modelo | Representa (registros) | Complexidade | Parâmetros |
|---|---|---|---|
| `gpu-triple-fan-v1` | 1.693 | média | comprimento, altura, slots, nº/tamanho de fans, backplate, conectores, cor |
| `gpu-dual-fan-v1` | 1.479 | média | idem |
| `gpu-single-fan-v1` | 544 | média | idem |
| `case-atx-mid-tower-v1` | 2.353 | alta | largura, altura, profundidade, shroud, vidro, slots de expansão, cor |
| `case-matx-tower-v1` | 642 | alta | idem |
| `case-atx-full-tower-v1` | 339 | alta | idem |
| `case-itx-tower-v1` | 337 | alta | idem |
| `mb-atx-v1` / `mb-matx-v1` / `mb-itx-v1` / `mb-eatx-v1` | 1.689 / 1.520 / 307 / 182 | média | formato, slots de RAM, M.2, x16, cor |
| `cooler-single-tower-v1` | 800 | média | altura, tamanho e nº de fans, cor |
| `cooler-dual-tower-v1` | 102 | média | idem |
| `cooler-low-profile-v1` | 231 | baixa | altura, tamanho do fan |
| `aio-radiator-120/240/280/360-v1` + `aio-pump-v1` | 132 / 455 / 112 / 568 | média | nº e tamanho de fans, cor; mangueiras geradas na cena |
| `ram-heatsink-v1` / `ram-rgb-v1` / `ram-standard-v1` | 2.205 / 1.721 / 807 | baixa | altura, dissipador, RGB, nº de pentes, cor |
| `psu-atx-v1` / `psu-sfx-v1` / `psu-sfx-l-v1` | 3.081 / 159 / 1 | baixa | comprimento, modular, cor |
| `fan-120-v1` / `fan-140-v1` | 2.114 / 833 | baixa | tamanho, RGB, cor |
| `ssd-m2-v1` / `drive-25-v1` / `hdd-35-v1` | 1.607 / 996 / 835 | trivial | comprimento do M.2 |

**Planejados** (entradas `status: planned` no catálogo):
- GPU blower, híbrida e passiva;
- gabinete SFF compacto e desktop/HTPC;
- radiador de 420 mm;
- fan de 92 mm.

Hoje os casos cobertos por aproximação são avisados: o resolvedor retorna um texto em `assumptions` quando usa o modelo mais parecido.

## 4. Arquitetura dos assets

```
3d/                                  gerador (Node 22.18+, TypeScript executado direto)
  src/analyze-opendb.ts              etapa 1: análise
  src/catalog.ts                     catálogo: família → variante → parâmetros base
  src/families/*.ts                  um gerador paramétrico por categoria
  src/kit/geometry.ts                primitivas (caixas arredondadas, pás de fan, aletas, heatpipes...)
  src/kit/materials.ts               materiais PBR por papel (body, accent, metal, glass, rgb...)
  src/kit/export.ts                  GLB + otimização (dedup, weld, quantização, meshopt)
  src/build.ts / src/validate.ts     geração e validação
frontend/src/lib/models3d/
  layout.ts                          FONTE ÚNICA das proporções (usada pelo gerador e pelo runtime)
  rig.ts                             adapta um GLB base às medidas de um componente
  assembly.ts                        monta o PC pelos pontos de montagem + checagens de colisão
  spec.ts / from-build.ts            contrato com o backend e conversão da configuração
frontend/public/3d-models/<categoria>/<variante>/<modelId>.glb (+ .lod1.glb) e catalog.json
backend …/visualization/ModelResolver.java   componente → família → variante → parâmetros
```

**Contrato de nós de cada GLB** (verificado por `validate.ts`):
```
<modelId>            raiz; extras: modelId, family, variant, baseParams
  <parte>            uma por parte do layout; posição = centro; extras: baseSize (mm)
    <parte>__mesh    geometria (a quantização pode pôr transformação aqui, nunca na parte)
  mount_<nome>       ponto de montagem vazio (cpu, ram_0, pcie_x16_0, psu, fan_front_0...)
```

### Como um modelo vira dezenas de componentes sem distorcer

`layout.ts` recebe os parâmetros do componente e devolve **posição e tamanho de cada parte**. O runtime escala
cada parte isoladamente (`tamanho / baseSize`). A proporção é decidida por regras, não por esticar o modelo:
- **GPU:**
  - o corpo e a PCB acompanham comprimento e altura;
  - a espessura segue o número de slots (× 20,32 mm);
  - as ventoinhas mantêm o formato redondo e são redistribuídas ao longo do comprimento;
  - o suporte traseiro tem tamanho padrão;
  - os conectores mantêm o tamanho real e ficam a 62% do comprimento.
- **Gabinete:** os painéis acompanham as medidas externas; os pontos de montagem (placa-mãe, fonte, fans, radiador) são recalculados a partir delas.
- **Cooler:** as aletas acompanham a altura; o fan mantém a proporção e fica alinhado ao topo.
- **Fan:** escala uniforme em X/Y, espessura fixa de 25 mm.

## 5. Pipeline de geração

```
dados do componente (OpenDB)
  → parâmetros   (ModelResolver no backend; só valores conhecidos + premissas declaradas)
  → layout.ts    (proporções)
  → gerador TS   (three.js → geometria procedural em mm)
  → glTF         (gltf-transform: materiais PBR por papel, nós nomeados, extras)
  → otimização   (dedup, weld, prune, quantização por malha, compressão EXT_meshopt)
  → frontend/public/3d-models/ (GLB alto + LOD1 baixo, catalog.json)
```

Comandos (na pasta `3d/`):

```bash
npm install
node src/analyze-opendb.ts ../data/opendb/<commit> [pasta CaseFan]   # etapa 1
node src/build.ts                  # gera todos os modelos (ou passe ids específicos)
node src/validate.ts               # contrato, dimensões, orçamento e varredura de parâmetros
node src/build.ts --spec spec.json # "assa" um GLB de um componente específico ({ modelId, params, out })
```

### Por que TypeScript e não Blender Python

O Blender não estava disponível e não é necessário para o nível de detalhe buscado: silhueta, proporção e
materiais. Com o gerador em TypeScript:
- o mesmo `layout.ts` define o modelo base e a adaptação em tempo de execução;
- ele roda em qualquer máquina e em CI;
- é reprodutível byte a byte.

**Quando usar Blender:** peças "vitrine" que precisem de modelagem manual (ex.: um gabinete específico de parceiro).
O arquivo exportado precisa seguir o mesmo contrato de nós e as mesmas medidas do layout. Assim, a mesma adaptação e as
mesmas checagens continuam valendo.

### Orçamento e otimização
- Limite por modelo: 60 mil triângulos e 400 KB. O maior hoje tem cerca de 8,7 mil triângulos e 75 KB.
- **LOD1** tem aletas e pás mais simples, com 30 a 50% menos triângulos, para celulares. O laboratório alterna entre os dois.
- Um PC completo tem cerca de 70 mil triângulos, 8 a 10 GLBs distintos e aproximadamente 450 KB de download.
- **Texturas:** nenhuma no v1. Materiais só com fator PBR deixam os arquivos pequenos e fáceis de recolorir. Logos e
  serigrafia ficam para depois, via KTX2.
- **Draw calls:** cerca de 230 por PC. Juntar malhas do mesmo material por modelo é a próxima otimização, se for preciso.

## 6. Associação componente → modelo

`ModelResolver` (backend, domínio puro) usa os campos estruturados. Nenhuma IA e nenhum palpite na interface.

```
RTX 4070 MSI VENTUS 2X (OpenDB: cooling "2 Fans", length 242, total_slot_width 2.15, color WHITE)
  → família gpu → variante dual-fan → gpu-dual-fan-v1
  → params { fanCount: 2, lengthMm: 242, slots: 2.0, eightPinConnectors: 1, heightMm: 120*, backplate: true }
  → cor WHITE · confiança "measured" · premissa: "*altura estimada: não está nos dados"
```

Cada item de configuração da API traz `models: ModelSpec[]` (um AIO traz radiador e bomba). O frontend
completa o que falta com os parâmetros base do catálogo. O teste `ModelResolverTest` garante que todo modelo que o
backend pode devolver existe no catálogo gerado.

## 7. Validação (etapa 5)

- **Offline** (`validate.ts`):
  - todo GLB respeita o contrato de nós;
  - a geometria cabe nas dimensões declaradas (tolerância de 4% ou 3 mm);
  - o orçamento de triângulos e tamanho é respeitado;
  - a varredura de parâmetros nos extremos medidos no OpenDB não gera tamanhos inválidos.

  A validação encontrou e forçou a correção de pás de ventoinha inclinadas, que passavam da espessura prevista em até 14 mm.
- **Cena de teste** `/laboratorio-3d`: quatro montagens (ATX com air cooler, full tower com 360, micro-ATX compacto e um
  conflito proposital). Para cada uma checa:
  - escala;
  - posicionamento pelos pontos de montagem;
  - colisões (placa de vídeo × cooler, fonte e discos; memória × cooler e radiador);
  - encaixe no espaço interno do gabinete;
  - desempenho (triângulos, draw calls, KB).

  O conflito proposital acusa: placa de vídeo 28 mm além do gabinete, cooler 9 mm além da tampa e placa de vídeo sobre a fonte.
- As checagens 3D são uma camada visual. **A autoridade sobre compatibilidade continua sendo o motor de regras.**

## 8. Como adicionar uma família

1. **Medir:** adicione o classificador em `analyze-opendb.ts` e confira quantos registros a família cobre. Se forem
   poucos, provavelmente não vale um modelo novo; use o mais parecido e declare a premissa.
2. **Layout:** em `layout.ts`, escreva `xxxLayout(params)`, que devolve partes (posição e tamanho em mm), pontos de
   montagem e limites. Documente o sistema de eixos. Só funções puras.
3. **Gerador:** em `3d/src/families/`, crie a geometria de cada parte no tamanho do layout, centrada, com materiais por papel.
4. **Catálogo:** em `catalog.ts`, adicione a entrada com `modelId` versionado (`-v1`), parâmetros base, `coverageKey`
   e descrição.
5. **Build e validação:** rode `node src/build.ts <modelId>` e depois `node src/validate.ts`.
6. **Runtime:** inclua a família em `spec.ts` (`layoutFor`) e, se ela montar em outro modelo, em `assembly.ts`.
7. **Backend:** faça o `ModelResolver` mapear os componentes para o novo modelo e adicione-o a `MODEL_IDS`. O teste de
   catálogo falha se o GLB não existir.
8. **Mudança incompatível:** gere um novo id (`-v2`) em vez de sobrescrever. Configurações salvas continuam válidas.

## 9. Licenciamento

- Toda a geometria é **criada do zero pelos nossos geradores**. Nenhum modelo, malha ou asset da BuildCores ou de
  terceiros foi baixado, extraído, modificado ou reutilizado.
- O OpenDB foi usado só para saber **quais componentes existem e suas medidas** (ODC-By, com atribuição mantida no produto).
- Referências visuais (fotos e páginas de fabricantes) servem apenas para entender proporções. Não copiamos texturas nem logos.
