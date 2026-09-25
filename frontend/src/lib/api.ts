import type {
  BuildView,
  Category,
  DataSources,
  Interpretation,
  Needs,
  Options,
  SavedBuildDocument,
  SearchResult,
  UpgradeAdvice,
  UpgradeGoals,
} from "./types";

/** Error with a user-facing message taken from the backend problem document. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

// In the browser, calls go to this origin and are relayed (app/api/[...path]/route.ts). On the server, call the
// backend directly, with the shared secret the backend requires in production (never exposed to the browser).
function baseUrl() {
  return typeof window === "undefined" ? (process.env.BACKEND_URL ?? "http://localhost:8080") : "";
}

function serverHeaders(): Record<string, string> {
  const secret = typeof window === "undefined" ? process.env.FRONTEND_SHARED_SECRET : undefined;
  return secret ? { "X-Frontend-Secret": secret } : {};
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl()}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...serverHeaders(), ...init?.headers },
      cache: "no-store",
    });
  } catch {
    throw new ApiError("Não conseguimos falar com o servidor. Verifique sua conexão e tente de novo.", 0);
  }
  if (!response.ok) {
    let detail = "Algo deu errado. Tente novamente em instantes.";
    try {
      const problem = await response.json();
      if (typeof problem?.detail === "string") detail = problem.detail;
    } catch {
      // keep generic message
    }
    throw new ApiError(detail, response.status);
  }
  return response.json() as Promise<T>;
}

export const api = {
  options: () => request<Options>("/api/options"),

  interpret: (text: string) =>
    request<Interpretation>("/api/intake/interpret", { method: "POST", body: JSON.stringify({ text }) }),

  recommend: (needs: Needs) =>
    request<BuildView>("/api/recommendations", { method: "POST", body: JSON.stringify(needs) }),

  evaluate: (componentIds: string[], ownedComponentIds: string[], needs?: Needs | null) =>
    request<BuildView>("/api/builds/evaluate", {
      method: "POST",
      body: JSON.stringify({ needs: needs ?? null, componentIds, ownedComponentIds }),
    }),

  save: (componentIds: string[], ownedComponentIds: string[], needs?: Needs | null, title?: string) =>
    request<{ id: string }>("/api/builds", {
      method: "POST",
      body: JSON.stringify({ needs: needs ?? null, componentIds, ownedComponentIds, title }),
    }),

  saved: (id: string) => request<SavedBuildDocument>(`/api/builds/${encodeURIComponent(id)}`),

  search: (category: Category | null, q: string, limit = 12) => {
    const params = new URLSearchParams({ q, limit: String(limit) });
    if (category) params.set("category", category);
    return request<SearchResult[]>(`/api/catalog/search?${params}`);
  },

  dataSources: () => request<DataSources>("/api/meta/data-sources"),

  component: (id: string) => request<SearchResult>(`/api/catalog/components/${encodeURIComponent(id)}`),

  upgrade: (currentComponentIds: string[], goals: UpgradeGoals, focus: Category | null) =>
    request<UpgradeAdvice>("/api/upgrades", {
      method: "POST",
      body: JSON.stringify({ currentComponentIds, goals, focus }),
    }),
};
