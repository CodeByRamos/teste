# Biblioteca de modelos 3D

Gerador procedural dos modelos base (família → GLB). Documentação completa: [`docs/3d/MODEL_LIBRARY.md`](../docs/3d/MODEL_LIBRARY.md).

```bash
npm install
node src/analyze-opendb.ts ../data/opendb/<commit>   # análise do OpenDB → docs/3d/opendb-analysis.json
node src/build.ts                                   # gera frontend/public/3d-models/** e catalog.json
node src/validate.ts                                # valida contrato, dimensões, orçamento e parâmetros
```

Requer Node 22.18+ (executa TypeScript direto, sem build). As proporções vêm de
`frontend/src/lib/models3d/layout.ts`, compartilhado com o runtime.
