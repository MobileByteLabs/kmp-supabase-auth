#!/usr/bin/env bash
# stage-docs-site.sh — assemble the docsify site into _site/.
#
# Extracted from docs-publish.yml so the PR docs gate and the deploy path stage the site the SAME
# way. Two copies of this logic would drift, and the drift would only show as a deployed site that
# is missing pages — the slowest possible way to find out.
#
# The site is an exact MIRROR of the repo's doc layout. That is deliberate: the docs reuse the
# repo's real Markdown, and mirroring means every relative link those files already contain
# resolves unchanged. Flattening to the site root was tried and broke 14 links, because
# `../TARGET_MATRIX.md` escapes the root.
set -euo pipefail

OUT="${1:-_site}"
mkdir -p "$OUT/docs"

# docsify's entry point + conventions belong at the SITE ROOT, not under docs/.
cp docs/index.html docs/_sidebar.md docs/_navbar.md docs/_coverpage.md "$OUT/"

# Authored pages keep their docs/ path.
for f in docs/*.md; do
  case "$(basename "$f")" in _sidebar.md | _navbar.md | _coverpage.md) continue ;; esac
  cp "$f" "$OUT/docs/"
done

# Root-level docs, at their repo paths.
# CHANGELOG included: it is the ONE document allowed to name versions (every other page defers to
# the Maven Central badge), so it is where the docs point a reader asking "which release changed
# this?" — a link the site must therefore be able to resolve.
cp README.md TARGET_MATRIX.md CONTRIBUTING.md CHANGELOG.md "$OUT/"
[ -f LICENSE ] && cp LICENSE "$OUT/"
[ -f CODE_OF_CONDUCT.md ] && cp CODE_OF_CONDUCT.md "$OUT/"

# Per-module docs, at their repo paths.
#
# STAGED is counted rather than assumed. An unmatched `cmp-*/` glob leaves the literal string,
# every `[ -f ]` fails, the loop stages nothing and the step still exits 0 — the same stale-glob
# failure that made development-md-coherence.yml pass while checking zero files. The site would
# deploy silently missing every module page.
STAGED=0
for m in cmp-*/; do
  [ -f "$m/README.md" ] || continue
  mkdir -p "$OUT/$m"
  cp "$m/README.md" "$OUT/$m/"
  [ -f "$m/DEVELOPMENT.md" ] && cp "$m/DEVELOPMENT.md" "$OUT/$m/"
  STAGED=$((STAGED + 1))
done

EXPECTED=$(grep -coE '^include\(":cmp-[a-z-]+"\)' settings.gradle.kts)
# The floor matters as much as the comparison: if BOTH the directories and the settings entries
# were renamed, STAGED and EXPECTED would agree at zero and this check would pass while staging an
# empty site.
if [ "$EXPECTED" -lt 1 ]; then
  echo "::error::settings.gradle.kts declares no cmp-* module — the pattern here is stale"
  exit 1
fi
if [ "$STAGED" -ne "$EXPECTED" ]; then
  echo "::error::staged $STAGED module doc set(s) but settings.gradle.kts declares $EXPECTED"
  exit 1
fi
echo "staged $STAGED module doc set(s)"

# Cloudflare Pages does not run Jekyll, so `.nojekyll` is not strictly required here — kept so the
# same _site/ can be served by GitHub Pages or `npx serve` unchanged.
touch "$OUT/.nojekyll"
