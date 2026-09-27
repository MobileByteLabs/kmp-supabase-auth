#!/usr/bin/env bash
# Every shipped version carries its own CHANGELOG section.
#
# The GitHub release body is assembled by release-notes.yml, which looks up `## [<version>]` in
# CHANGELOG.md and prepends it above the auto-generated artifact list. If that section is absent
# the release ships as a bare list of jars — technically a release, useless to a reader.
#
# Upstream's `fail-on-missing` now makes that a hard error, but it fires at RELEASE time, when
# the tag already exists and the fix means editing a published release. This gate runs on every
# PR instead, so the section is written while the change is still in review — the version bump
# and the note describing it land together or not at all.
set -euo pipefail

# --since <ref> adds the PR-scoped check: a change to shippable code must bring a changelog
# entry with it. Without it the release section only ever gets written at release time, from
# memory, which is how release notes end up as "various fixes".
SINCE=""
CHANGELOG="CHANGELOG.md"
while [ $# -gt 0 ]; do
  case "$1" in
    --since) SINCE="$2"; shift 2 ;;
    *)       CHANGELOG="$1"; shift ;;
  esac
done
VERSION="$(grep -E '^supabaseauth\.version=' gradle.properties | cut -d= -f2)"

if [ -z "$VERSION" ]; then
  echo "::error::could not read supabaseauth.version from gradle.properties"
  exit 1
fi

if [ ! -f "$CHANGELOG" ]; then
  echo "::error::$CHANGELOG does not exist, but release-notes.yml requires it"
  exit 1
fi

# `## [0.1.0]` with an optional trailing date. Anchored so `## [0.1.0-rc1]` never satisfies 0.1.0.
if ! grep -qE "^## \[${VERSION//./\\.}\]( |$)" "$CHANGELOG"; then
  {
    echo "::error::$CHANGELOG has no '## [$VERSION]' section"
    echo "gradle.properties declares supabaseauth.version=$VERSION, so the release built from"
    echo "this commit would be published with no release notes. Add the section describing"
    echo "what changed, then re-run."
    echo ""
    echo "Sections currently present:"
    grep -E '^## \[' "$CHANGELOG" | sed 's/^/  /' || echo "  (none)"
  } >&2
  exit 1
fi

# A heading with nothing under it satisfies the lookup and still produces an empty release body.
BODY="$(awk -v v="## [$VERSION]" '
  index($0, v) == 1 { found = 1; next }
  found && /^## / { exit }
  found { print }
' "$CHANGELOG" | tr -d '[:space:]')"

if [ -z "$BODY" ]; then
  echo "::error::'## [$VERSION]' in $CHANGELOG is empty — an empty section produces an empty release body" >&2
  exit 1
fi

echo "changelog gate: '## [$VERSION]' present with $(printf '%s' "$BODY" | wc -c | tr -d ' ') chars of content"

# ── PR-scoped: shippable change ⇒ changelog change ───────────────────────────────────────────
[ -n "$SINCE" ] || exit 0

if ! git rev-parse --verify --quiet "$SINCE" >/dev/null; then
  echo "::error::base ref '$SINCE' is not available — fetch it before running this check" >&2
  exit 1
fi

CHANGED="$(git diff --name-only "$SINCE"...HEAD)"

# Only code that actually ships to a consumer demands a note. Docs, CI and build-logic changes
# are real work but not something a release reader needs to be told about.
SHIPPABLE="$(printf '%s\n' "$CHANGED" | grep -E '^cmp-[a-z-]+/src/|^gradle\.properties$|^gradle/libs\.versions\.toml$' || true)"

if [ -z "$SHIPPABLE" ]; then
  echo "changelog gate: no shippable change in this diff — no entry required"
  exit 0
fi

if ! printf '%s\n' "$CHANGED" | grep -qx "$CHANGELOG"; then
  {
    echo "::error::this PR changes shippable code but does not update $CHANGELOG"
    echo "Add an entry under '## [Unreleased]' describing the change. It is copied into the"
    echo "GitHub release body verbatim when this version ships, so write it for a reader who"
    echo "was not in the PR."
    echo ""
    echo "Shippable files changed:"
    printf '%s\n' "$SHIPPABLE" | sed 's/^/  /'
  } >&2
  exit 1
fi

echo "changelog gate: shippable change is accompanied by a $CHANGELOG update"
