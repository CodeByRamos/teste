#!/usr/bin/env bash
# Fetches a pinned snapshot of BuildCores OpenDB (hardware specs only) into data/opendb/<commit>.
#
# Usage: scripts/fetch-opendb.sh [commit-sha]
#   OPENDB_LOCAL_REPO  path to an existing clone that contains the commit (skips the download)
#
# Only the PC-building categories are extracted. The upstream LICENSE.txt is kept next to the
# data: ODC-By 1.0 requires license notices to stay intact.
set -euo pipefail

DEFAULT_COMMIT="399c7168eac711f70b8a9ae98e972d7188454f46"
COMMIT="${1:-$DEFAULT_COMMIT}"
PUBLIC_REPO_URL="https://github.com/buildcores/buildcores-open-db"
CATEGORIES=(CPU GPU Motherboard RAM Storage PSU PCCase CPUCooler)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$ROOT_DIR/data/opendb/$COMMIT"

if [[ -f "$TARGET/SNAPSHOT.json" ]]; then
  echo "Snapshot $COMMIT already present at $TARGET"
  echo "$COMMIT" > "$ROOT_DIR/data/opendb/CURRENT"
  exit 0
fi

PATHS=("LICENSE.txt")
for category in "${CATEGORIES[@]}"; do
  PATHS+=("open-db/$category")
done

STAGING="$(mktemp -d)"
trap 'rm -rf "$STAGING"' EXIT

if [[ -n "${OPENDB_LOCAL_REPO:-}" ]]; then
  git -C "$OPENDB_LOCAL_REPO" archive --format=tar "$COMMIT" "${PATHS[@]}" | tar -x -C "$STAGING"
  COMMITTED_AT="$(git -C "$OPENDB_LOCAL_REPO" show -s --format=%cI "$COMMIT")"
else
  curl -fsSL "https://codeload.github.com/buildcores/buildcores-open-db/tar.gz/$COMMIT" \
    | tar -xz -C "$STAGING" --strip-components=1 --wildcards "*/LICENSE.txt" $(printf '*/open-db/%s/* ' "${CATEGORIES[@]}")
  COMMITTED_AT="$(curl -fsSL "https://api.github.com/repos/buildcores/buildcores-open-db/commits/$COMMIT" \
    | sed -n 's/.*"date": *"\([^"]*\)".*/\1/p' | head -1)"
fi

mkdir -p "$TARGET"
cp "$STAGING/LICENSE.txt" "$TARGET/LICENSE.txt"
for category in "${CATEGORIES[@]}"; do
  mv "$STAGING/open-db/$category" "$TARGET/$category"
done

cat > "$TARGET/SNAPSHOT.json" <<JSON
{
  "source": "buildcores-opendb",
  "repository": "$PUBLIC_REPO_URL",
  "commit": "$COMMIT",
  "committedAt": "$COMMITTED_AT",
  "fetchedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "license": "ODC-By-1.0",
  "categories": [$(printf '"%s",' "${CATEGORIES[@]}" | sed 's/,$//')]
}
JSON

echo "$COMMIT" > "$ROOT_DIR/data/opendb/CURRENT"
echo "OpenDB snapshot $COMMIT ready at $TARGET"
