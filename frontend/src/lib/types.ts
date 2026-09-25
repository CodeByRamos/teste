// Mirrors the backend API documents (app.platform.api.ApiViews).

export type Category =
  | "CPU"
  | "GPU"
  | "MOTHERBOARD"
  | "MEMORY"
  | "STORAGE"
  | "POWER_SUPPLY"
  | "CASE"
  | "CPU_COOLER";

export type UseCase =
  | "GAMING_COMPETITIVE"
  | "GAMING_AAA"
  | "PROGRAMMING"
  | "CONTAINERS_VMS"
  | "VIDEO_EDITING"
  | "STREAMING"
  | "OFFICE_STUDY";

export type Resolution = "FULL_HD" | "QHD" | "UHD_4K";
export type Status = "OK" | "WARNING" | "INCOMPATIBLE";

export interface Labeled {
  value: string;
  label: string;
}

export interface Needs {
  budgetBrl: number;
  useCases: UseCase[];
  primaryUse?: UseCase | null;
  resolution?: Resolution | null;
  ownedComponentIds?: string[];
}

export interface Price {
  amountBrl: number;
  kind: "REAL" | "EXAMPLE";
  isExample: boolean;
  storeName: string;
  url: string | null;
  observedAt: string;
}

export interface Spec {
  label: string;
  value: string;
}

export interface ComponentView {
  id: string;
  name: string;
  manufacturer: string | null;
  category: Category;
  categoryLabel: string;
  releaseYear: number | null;
  source: { name: string; externalId: string; recordUrl: string | null; license: string };
  quality: { score: number; issues: { field: string; kind: string; detail: string | null }[] };
}

export interface Alternative {
  component: { id: string; name: string; category: Category; categoryLabel: string };
  priceBrl: number;
  priceDeltaBrl: number;
  direction: "CHEAPER" | "BETTER";
  impact: string;
}

export interface BuildItem {
  category: Category;
  categoryLabel: string;
  owned: boolean;
  component: ComponentView;
  price: Price | null;
  explanation: { whatItIs: string; whyItMatters: string; reason: string };
  specs: Spec[];
  alternatives: Alternative[];
  /** 3D models that draw this part (resolved by the backend); an AIO has a radiator and a pump. */
  models?: ModelSpecDto[];
}

export interface ModelSpecDto {
  modelId: string;
  family: string;
  params: Record<string, unknown>;
  color: string | null;
  rgb: boolean;
  confidence: "measured" | "estimated";
  assumptions: string[];
}

export interface Finding {
  ruleId: string;
  status: Status;
  involves: Category[];
  title: string;
  explanation: string;
  technicalDetail: string | null;
  verified: boolean;
}

export interface BuildView {
  needs: {
    budgetBrl: number;
    useCases: Labeled[];
    primaryUse: Labeled | null;
    resolution: Labeled;
    ownedComponentIds: string[];
  } | null;
  requirements: string[];
  items: BuildItem[];
  compatibility: {
    overall: Status;
    summary: string;
    findings: Finding[];
    power: { estimatedLoadWatts: number; recommendedPsuWatts: number; complete: boolean };
  };
  totals: {
    totalBrl: number;
    budgetBrl: number | null;
    withinBudget: boolean | null;
    allPriced: boolean;
    pricesAreExamples: boolean;
  };
  notes: string[];
  dataSource: { name: string; version: string; url: string | null; license: string; licenseUrl: string; ingestedAt: string | null };
  disclaimers: { prices: string | null; performance: string };
  engines: { recommendation: string; compatibility: string };
}

export interface SavedBuildDocument {
  id: string;
  savedAt: string;
  title: string | null;
  build: BuildView;
}

export interface SearchResult {
  id: string;
  name: string;
  manufacturer: string | null;
  category: Category;
  categoryLabel: string;
  releaseYear: number | null;
  price: Price | null;
  highlights: Spec[];
  qualityScore: number;
}

export interface Interpretation {
  budgetBrl: number | null;
  useCases: Labeled[];
  resolution: Labeled | null;
  mentionsOwnedParts: boolean;
  understood: string[];
  questions: string[];
}

export interface Options {
  useCases: { value: UseCase; label: string; description: string }[];
  resolutions: { value: Resolution; label: string }[];
  categories: { value: Category; label: string }[];
  minBudgetBrl: number;
  maxBudgetBrl: number;
}

export interface DataSources {
  hardware: { name: string; version: string; url: string | null; license: string; licenseUrl: string; ingestedAt: string | null };
  components: number;
  attribution: string;
  pricesDisclaimer: string;
  quality?: Record<
    string,
    { records: number; rejected: number; averageQuality: number; issues: Record<string, Record<string, number>> }
  >;
}

export type AssessmentLevel = "GOOD" | "ENOUGH" | "WEAK" | "BOTTLENECK";

export interface ComponentSummary {
  id: string;
  name: string;
  category: Category;
  categoryLabel: string;
}

export interface UpgradePlan {
  kind: "GPU" | "CPU" | "PLATFORM" | "MEMORY" | "STORAGE" | "COMBINED";
  title: string;
  impact: string;
  costBrl: number;
  changes: {
    category: Category;
    categoryLabel: string;
    role: "MAIN" | "REQUIRED";
    replaces: ComponentSummary | null;
    part: ComponentSummary;
    price: Price | null;
    reason: string;
  }[];
  kept: ComponentSummary[];
  dependencies: string[];
  after: BuildView;
}

export interface UpgradeAdvice {
  assessment: {
    category: Category;
    categoryLabel: string;
    component: ComponentSummary | null;
    level: AssessmentLevel;
    title: string;
    explanation: string;
  }[];
  recommended: UpgradePlan | null;
  alternatives: UpgradePlan[];
  notes: string[];
  disclaimers: { prices: string | null; performance: string };
}

export interface UpgradeGoals {
  budgetBrl: number;
  useCases: UseCase[];
  primaryUse?: UseCase | null;
  resolution?: Resolution | null;
}
