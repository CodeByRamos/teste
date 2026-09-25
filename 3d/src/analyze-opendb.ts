// Step 1 of the model library: measure what the OpenDB catalog actually contains, so model families are chosen
// by coverage (how many real components one parametric model can represent), not by guesswork.
//
// Usage: node src/analyze-opendb.ts <snapshot-dir> [extra-category-dir...]
//   <snapshot-dir>   data/opendb/<commit> (8 PC categories)
//   extra dirs       optional OpenDB category folders (e.g. CaseFan) from a full checkout
//
// Output: docs/3d/opendb-analysis.json (machine-readable, feeds the report and the catalog).
//
// "Popularity" has no direct field in OpenDB. Two proxies are used and reported separately:
//   - current:   released 2020+ (when known) or belonging to a current product family
//   - listed:    number of retailer listings (products stocked by many stores are the ones people buy)

import { readdirSync, readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { join, resolve, basename } from "node:path";

type Rec = Record<string, any>;

const [snapshotDir, ...extraDirs] = process.argv.slice(2);
if (!snapshotDir) {
  console.error("usage: node src/analyze-opendb.ts <snapshot-dir> [extra-category-dir...]");
  process.exit(1);
}

function load(dir: string): Rec[] {
  return readdirSync(dir)
    .filter((f) => f.endsWith(".json"))
    .map((f) => JSON.parse(readFileSync(join(dir, f), "utf8")));
}

const dirs: Record<string, string> = {};
for (const name of readdirSync(snapshotDir)) {
  if (existsSync(join(snapshotDir, name)) && !name.includes(".")) dirs[name] = join(snapshotDir, name);
}
for (const extra of extraDirs) dirs[basename(extra)] = extra;

const data: Record<string, Rec[]> = {};
for (const [name, dir] of Object.entries(dirs)) data[name] = load(dir);

// ---------------------------------------------------------------------------------------------
// helpers

const listings = (r: Rec) => (r.identifiers?.retailer_listings ?? []).length;
const year = (r: Rec) => (typeof r.metadata?.releaseYear === "number" ? r.metadata.releaseYear : null);

function histogram<T>(items: Rec[], key: (r: Rec) => T | T[] | null | undefined, top = 20) {
  const counts = new Map<string, number>();
  for (const item of items) {
    let value: unknown;
    try {
      value = key(item);
    } catch {
      value = "<error>";
    }
    for (const v of Array.isArray(value) ? value : [value]) {
      const k = v === null || v === undefined || v === "" ? "<unknown>" : String(v);
      counts.set(k, (counts.get(k) ?? 0) + 1);
    }
  }
  return Object.fromEntries([...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, top));
}

function stats(values: number[]) {
  const v = values.filter((x) => Number.isFinite(x)).sort((a, b) => a - b);
  if (v.length === 0) return null;
  const q = (p: number) => v[Math.min(v.length - 1, Math.floor(p * (v.length - 1)))];
  return { n: v.length, min: v[0], p10: q(0.1), median: q(0.5), p90: q(0.9), max: v[v.length - 1] };
}

function bucket(value: number | null | undefined, edges: number[], labels: string[]) {
  if (value === null || value === undefined || !Number.isFinite(value)) return "<unknown>";
  for (let i = 0; i < edges.length; i++) if (value < edges[i]) return labels[i];
  return labels[labels.length - 1];
}

/** Weighted coverage: every record counts 1; records with retailer listings count more (a stocked product). */
function weight(r: Rec) {
  return 1 + Math.min(listings(r), 20) / 5;
}

function coverage(items: Rec[], family: (r: Rec) => string | null) {
  const out: Record<string, { records: number; weighted: number; current: number }> = {};
  for (const r of items) {
    const f = family(r);
    if (!f) continue;
    out[f] ??= { records: 0, weighted: 0, current: 0 };
    out[f].records++;
    out[f].weighted += weight(r);
    if ((year(r) ?? 0) >= 2020) out[f].current++;
  }
  for (const v of Object.values(out)) v.weighted = Math.round(v.weighted);
  return Object.fromEntries(Object.entries(out).sort((a, b) => b[1].records - a[1].records));
}

// ---------------------------------------------------------------------------------------------
// family classifiers (the same rules the backend resolver uses; see docs/3d/MODEL_LIBRARY.md)

const gpuFans = (r: Rec): number | "blower" | "liquid" | "passive" | null => {
  const text = String(r.cooling ?? "").toLowerCase();
  if (/blower/.test(text)) return "blower";
  if (/liquid|water|hybrid/.test(text)) return "liquid";
  if (/passive|fanless/.test(text)) return "passive";
  const m = text.match(/(\d)\s*fan/);
  if (m) return Number(m[1]);
  if (typeof r.fan_quantity === "number") return r.fan_quantity;
  return null;
};

const gpuFamily = (r: Rec) => {
  const fans = gpuFans(r);
  if (fans === 1) return "gpu-single-fan";
  if (fans === 2) return "gpu-dual-fan";
  if (fans === 3 || fans === 4) return "gpu-triple-fan";
  if (fans === "blower") return "gpu-blower";
  if (fans === "passive") return "gpu-passive";
  if (fans === "liquid") return "gpu-liquid";
  return "gpu-dual-fan (default: cooling unknown)";
};

const caseFamily = (r: Rec) => {
  const f = String(r.form_factor ?? "");
  if (/Full Tower/.test(f)) return "case-atx-full-tower";
  if (/^(ATX|EATX) Mid Tower/.test(f)) return "case-atx-mid-tower";
  if (/Micro ATX (Mini|Mid|Slim) Tower/.test(f) || f === "ATX Mini Tower") return "case-matx-tower";
  if (/Mini ITX Tower/.test(f)) return "case-itx-tower";
  if (/Desktop|HTPC|Test Bench|Open Frame/.test(f)) return "case-desktop-htpc (later)";
  return null;
};

const boardFamily = (r: Rec) => {
  const f = String(r.form_factor ?? "");
  if (f === "ATX") return "mb-atx";
  if (f === "Micro ATX") return "mb-matx";
  if (/Mini-ITX|Mini DTX/.test(f)) return "mb-itx";
  if (/EATX|SSI|XL ATX|HPTX/.test(f)) return "mb-eatx";
  return null;
};

const coolerFamily = (r: Rec) => {
  if (r.water_cooled === true) {
    const size = r.radiator_size;
    return [120, 140, 240, 280, 360, 420].includes(size) ? `aio-${size}` : "aio-other";
  }
  const h = r.height;
  if (typeof h !== "number") return "air-unknown-height";
  if (h < 80) return "cooler-low-profile";
  const fans = r.fan_quantity ?? 1;
  const dual = /dual|twin|D15|D14|Phantom Spirit|Peerless Assassin|FUMA|Dark Rock Pro|AK620|AK500? Digital Pro|NH-D/i.test(
    String(r.metadata?.name ?? ""),
  );
  return dual || fans >= 2 && h >= 150 ? "cooler-dual-tower" : "cooler-single-tower";
};

const ramFamily = (r: Rec) => {
  if (/SO-DIMM/.test(String(r.form_factor))) return "ram-sodimm (not modeled)";
  if (r.rgb === true) return "ram-rgb";
  if (r.heat_spreader === true) return "ram-heatsink";
  return "ram-standard";
};

const psuFamily = (r: Rec) => {
  const f = String(r.form_factor ?? "");
  if (f === "ATX" || f === "ATX/EPS") return "psu-atx";
  if (f === "SFX") return "psu-sfx";
  if (f === "SFX-L") return "psu-sfx-l";
  return f ? `psu-other (${f})` : null;
};

const storageFamily = (r: Rec) => {
  const f = String(r.form_factor ?? "");
  if (f.startsWith("M.2")) return "ssd-m2";
  if (f === '2.5"') return String(r.storage_type) === "SSD" ? "ssd-sata-25" : "hdd-25";
  if (f === '3.5"') return "hdd-35";
  return `storage-other (${f || "unknown"})`;
};

const fanFamily = (r: Rec) => {
  const size = r.size ?? r.fan_size;
  if (size === 120) return "fan-120";
  if (size === 140) return "fan-140";
  return typeof size === "number" ? `fan-${size} (later)` : "fan-unknown";
};

// ---------------------------------------------------------------------------------------------

const gpu = data.GPU ?? [];
const cases = data.PCCase ?? [];
const boards = data.Motherboard ?? [];
const coolers = data.CPUCooler ?? [];
const ram = data.RAM ?? [];
const psu = data.PSU ?? [];
const storage = data.Storage ?? [];
const fans = data.CaseFan ?? [];

const analysis = {
  generatedAt: new Date().toISOString(),
  source: resolve(snapshotDir),
  counts: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v.length])),
  popularityProxies: {
    listedShareByCategory: Object.fromEntries(
      Object.entries(data).map(([k, v]) => [k, Math.round((100 * v.filter((r) => listings(r) > 0).length) / Math.max(1, v.length))]),
    ),
  },
  gpu: {
    cooling: histogram(gpu, (r) => r.cooling, 15),
    fans: histogram(gpu, gpuFans),
    lengthMm: stats(gpu.map((r) => r.length)),
    lengthBuckets: histogram(gpu, (r) => bucket(r.length, [200, 250, 300, 340], ["<200", "200-249", "250-299", "300-339", "340+"])),
    slotWidth: histogram(gpu, (r) => r.total_slot_width),
    slotBuckets: histogram(gpu, (r) => bucket(r.total_slot_width, [2.1, 2.6, 3.1, 3.6], ["2", "2.5", "3", "3.5", "4"])),
    lengthByFans: {
      "1": stats(gpu.filter((r) => gpuFans(r) === 1).map((r) => r.length)),
      "2": stats(gpu.filter((r) => gpuFans(r) === 2).map((r) => r.length)),
      "3": stats(gpu.filter((r) => gpuFans(r) === 3).map((r) => r.length)),
    },
    color: histogram(gpu, (r) => r.color?.[0], 8),
    powerConnector16Pin: gpu.filter((r) => (r.power_connectors?.pcie_12VHPWR ?? 0) + (r.power_connectors?.pcie_12V_2x6 ?? 0) > 0).length,
    families: coverage(gpu, gpuFamily),
  },
  case: {
    formFactor: histogram(cases, (r) => r.form_factor),
    dimensionsByFamily: Object.fromEntries(
      ["case-atx-full-tower", "case-atx-mid-tower", "case-matx-tower", "case-itx-tower"].map((f) => {
        const subset = cases.filter((r) => caseFamily(r) === f && r.dimensions_mm);
        return [f, {
          depth: stats(subset.map((r) => r.dimensions_mm.depth)),
          width: stats(subset.map((r) => r.dimensions_mm.width)),
          height: stats(subset.map((r) => r.dimensions_mm.height)),
          maxGpu: stats(cases.filter((r) => caseFamily(r) === f).map((r) => r.max_video_card_length)),
        }];
      }),
    ),
    sidePanel: histogram(cases, (r) => r.side_panel, 8),
    transparentSide: histogram(cases, (r) => r.has_transparent_side_panel),
    color: histogram(cases, (r) => r.color?.[0], 6),
    families: coverage(cases, caseFamily),
  },
  motherboard: {
    formFactor: histogram(boards, (r) => r.form_factor),
    memorySlotsByForm: {
      ATX: histogram(boards.filter((r) => r.form_factor === "ATX"), (r) => r.memory?.slots),
      "Micro ATX": histogram(boards.filter((r) => r.form_factor === "Micro ATX"), (r) => r.memory?.slots),
      "Mini-ITX": histogram(boards.filter((r) => r.form_factor === "Mini-ITX"), (r) => r.memory?.slots),
    },
    m2Slots: histogram(boards, (r) => r.m2_slots?.length ?? null),
    color: histogram(boards, (r) => r.color?.[0], 6),
    families: coverage(boards, boardFamily),
  },
  cooler: {
    waterCooled: histogram(coolers, (r) => r.water_cooled),
    radiator: histogram(coolers.filter((r) => r.water_cooled), (r) => r.radiator_size),
    airHeightMm: stats(coolers.filter((r) => r.water_cooled === false).map((r) => r.height)),
    airHeightBuckets: histogram(coolers.filter((r) => r.water_cooled === false),
      (r) => bucket(r.height, [60, 80, 130, 150, 170], ["<60", "60-79", "80-129", "130-149", "150-169", "170+"])),
    fanSize: histogram(coolers, (r) => r.fan_size),
    fanQuantity: histogram(coolers, (r) => r.fan_quantity),
    families: coverage(coolers, coolerFamily),
  },
  ram: {
    modules: histogram(ram, (r) => r.modules?.quantity),
    heatSpreader: histogram(ram, (r) => r.heat_spreader),
    rgb: histogram(ram, (r) => r.rgb),
    heightMm: stats(ram.map((r) => r.height)),
    color: histogram(ram, (r) => r.color?.[0], 6),
    families: coverage(ram, ramFamily),
  },
  psu: {
    formFactor: histogram(psu, (r) => r.form_factor),
    lengthByForm: {
      ATX: stats(psu.filter((r) => r.form_factor === "ATX").map((r) => r.length)),
      SFX: stats(psu.filter((r) => r.form_factor === "SFX").map((r) => r.length)),
    },
    modular: histogram(psu, (r) => r.modular),
    color: histogram(psu, (r) => r.color?.[0], 6),
    families: coverage(psu, psuFamily),
  },
  storage: {
    formFactor: histogram(storage, (r) => r.form_factor),
    families: coverage(storage, storageFamily),
  },
  fan: fans.length
    ? {
        size: histogram(fans, (r) => r.size ?? r.fan_size),
        quantityPerPack: histogram(fans, (r) => r.quantity),
        lighting: histogram(fans, (r) => r.lighting?.[0] ?? r.led, 8),
        color: histogram(fans, (r) => r.color?.[0], 6),
        sampleKeys: Object.keys(fans[0]).filter((k) => !["identifiers", "metadata", "general_product_information"].includes(k)),
        families: coverage(fans, fanFamily),
      }
    : null,
};

const out = resolve("..", "docs", "3d");
mkdirSync(out, { recursive: true });
writeFileSync(join(out, "opendb-analysis.json"), JSON.stringify(analysis, null, 2));
console.log(JSON.stringify(analysis, null, 1));
