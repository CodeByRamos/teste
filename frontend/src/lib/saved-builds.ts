// Saved builds are stored server-side; this browser-only list remembers which ones this person saved,
// until accounts exist. Storage can be unavailable (private mode), so every access is guarded.

export interface SavedBuildEntry {
  id: string;
  title: string;
  totalBrl: number;
  savedAt: string;
}

const KEY = "saved-builds";

export function listSavedBuilds(): SavedBuildEntry[] {
  try {
    const raw = window.localStorage.getItem(KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function rememberSavedBuild(entry: SavedBuildEntry) {
  try {
    const entries = [entry, ...listSavedBuilds().filter((existing) => existing.id !== entry.id)].slice(0, 50);
    window.localStorage.setItem(KEY, JSON.stringify(entries));
  } catch {
    // The build is still saved on the server and reachable by its link.
  }
}

export function forgetSavedBuild(id: string) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(listSavedBuilds().filter((entry) => entry.id !== id)));
  } catch {
    // ignore
  }
}
