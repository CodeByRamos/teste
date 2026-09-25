# Frontend

Next.js 16 (App Router) + React 19 + Tailwind 4. Somente apresentação: toda regra de negócio está no backend.

```bash
npm install
npm run dev      # http://localhost:3000 — /api/* é encaminhado para BACKEND_URL (padrão http://localhost:8080)
npm run lint
npm run build
```

Variáveis: `BACKEND_URL` (servidor), `NEXT_PUBLIC_APP_NAME` (nome exibido, provisório).
Identidade visual: tokens de cor em `src/app/globals.css`, nome em `src/config/brand.ts`.
