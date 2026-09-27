#!/usr/bin/env bash
# No document states the library version as a literal. The Maven Central badge is the SoT.
#
# A version written into prose is correct for exactly one release. After the next one it is a
# confident lie: a reader copies `…:0.1.1` long after 0.2.0 shipped, and nothing in the repo
# looks wrong. The badge is read from the registry at page load, so it cannot go stale — which
# is why kmp-toolkit's README contains zero occurrences of its own version.
#
# `gradle.properties` is the ONE place the number lives, because the build needs it there.
set -euo pipefail

VERSION="$(grep -E '^supabaseauth\.version=' gradle.properties | cut -d= -f2)"
[ -n "$VERSION" ] || { echo "::error::cannot read supabaseauth.version"; exit 1; }

# CHANGELOG is exempt: a changelog is a historical record, and its version headings are the
# point. Everything else describes the CURRENT release and must defer to the badge.
FILES=$(git ls-files '*.md' | grep -vE '^CHANGELOG\.md$' || true)
[ -n "$FILES" ] || { echo "::error::no markdown files found — pattern is stale"; exit 1; }

# -F: the version contains dots, which as a regex would match unrelated hex like #0b1f18.
HITS=$(grep -nF "$VERSION" $FILES 2>/dev/null || true)

if [ -n "$HITS" ]; then
  {
    echo "::error::the library version is hardcoded in documentation"
    echo "Found '$VERSION' written as a literal. Docs must point at the Maven Central badge,"
    echo "which is live from the registry, instead of freezing a number that goes stale on the"
    echo "next release. Generated blocks: fix the generator in scripts/docs/refresh.sh."
    echo ""
    printf '%s\n' "$HITS" | sed 's/^/  /'
  } >&2
  exit 1
fi

echo "no-hardcoded-version: $(printf '%s\n' "$FILES" | wc -l | tr -d ' ') markdown file(s) carry no literal version"
