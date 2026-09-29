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

# No pipes between producers and early-exiting consumers: with `pipefail`, a consumer that stops reading
# (head, sed q, tar finishing early) kills the producer with SIGPIPE and fails the build (exit 141).
COMMITTED_AT=""
if [[ -n "${OPENDB_LOCAL_REPO:-}" ]]; then
  git -C "$OPENDB_LOCAL_REPO" archive --format=tar -o "$STAGING/snapshot.tar" "$COMMIT" "${PATHS[@]}"
  tar -x -C "$STAGING" -f "$STAGING/snapshot.tar"
  COMMITTED_AT="$(git -C "$OPENDB_LOCAL_REPO" show -s --format=%cI "$COMMIT")"
else
  curl -fsSL --retry 3 --retry-delay 5 -o "$STAGING/snapshot.tar.gz" \
    "https://codeload.github.com/buildcores/buildcores-open-db/tar.gz/$COMMIT"
  tar -xz -C "$STAGING" -f "$STAGING/snapshot.tar.gz" --strip-components=1 --wildcards \
    "*/LICENSE.txt" $(printf '*/open-db/%s/* ' "${CATEGORIES[@]}")
  # Commit date is informational only. The GitHub API rate-limits anonymous calls (shared build machines hit it),
  # so a failure here records "unknown" instead of failing the build.
  if COMMIT_JSON="$(curl -fsSL --max-time 20 "https://api.github.com/repos/buildcores/buildcores-open-db/commits/$COMMIT")"; then
    if [[ "$COMMIT_JSON" =~ \"date\":[[:space:]]*\"([^\"]+)\" ]]; then
      COMMITTED_AT="${BASH_REMATCH[1]}"
    fi
  fi
fi
if [[ -n "$COMMITTED_AT" ]]; then
  COMMITTED_AT_JSON="\"$COMMITTED_AT\""
else
  COMMITTED_AT_JSON="null"
  echo "Commit date unavailable (GitHub API); recording it as unknown."
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
  "committedAt": $COMMITTED_AT_JSON,
  "fetchedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "license": "ODC-By-1.0",
  "categories": [$(IFS=,; printf '"%s"' "${CATEGORIES[*]}" | sed 's/,/","/g')]
}
JSON

echo "$COMMIT" > "$ROOT_DIR/data/opendb/CURRENT"
echo "OpenDB snapshot $COMMIT ready at $TARGET"
