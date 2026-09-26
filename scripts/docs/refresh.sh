#!/usr/bin/env bash
# scripts/docs/refresh.sh — ONE entry point that brings the docs back in step with the build.
#
# WHY THIS EXISTS
# ───────────────
# Three facts in this repo's docs are already stated authoritatively by the build — the library
# VERSION (gradle.properties), each module's TARGET SET (its build.gradle.kts) and its PUBLIC API
# (the committed BCV baseline). Restating them in prose is how the README came to claim 8 targets
# and a Store5 dependency weeks after both had changed, while CI stayed green: nothing was
# checking, because nothing could.
#
# So those three are GENERATED, between markers, and a gate re-measures them. Everything else —
# why the library exists, what not to do, the console setup guides — is authored prose the
# generator never touches.
#
# CONTRACT
#   • writes ONLY between `<!-- docs-gen:<name>:begin -->` and `<!-- …:end -->`
#   • authored prose outside markers is preserved byte-for-byte
#   • `--check` verifies without writing, and prints the one command that fixes drift
#   • pure bash + python3: no JDK, no Gradle, no network, so CI needs nothing installed
#
# Usage:  bash scripts/docs/refresh.sh [--check]

set -euo pipefail
cd "$(dirname "$0")/../.."
# shellcheck source=scripts/docs/lib.sh
source scripts/docs/lib.sh

CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

VERSION="$(lib_version)"

# ── generators ───────────────────────────────────────────────────────────────────────────────

gen_modules_table() {
  {
    echo "| Module | Artifact | Targets |"
    echo "|---|---|---|"
    for m in $(lib_modules); do
      echo "| [\`$m\`]($m/README.md) | \`io.github.mobilebytelabs:$m:$VERSION\` | $(lib_target_count "$m") |"
    done
    echo "| \`sample-app\` | — (not published) | — |"
  } > "$TMP/modules.md"
}

gen_install() {
  {
    echo '```kotlin'
    echo 'dependencies {'
    for m in $(lib_modules); do
      case "$m" in
        *-compose) note="  // Compose UI (optional)" ;;
        *)         note="  // headless" ;;
      esac
      echo "    implementation(\"io.github.mobilebytelabs:$m:$VERSION\")$note"
    done
    echo '}'
    echo '```'
  } > "$TMP/install.md"
}

gen_targets_table() {
  {
    echo "| Module | Count | Targets |"
    echo "|---|---|---|"
    for m in $(lib_modules); do
      echo "| \`$m\` | **$(lib_target_count "$m")** | $(lib_targets "$m" | sed 's/^/`/;s/$/`/' | tr '\n' ' ') |"
    done
  } > "$TMP/targets.md"
}

gen_deps_table() {
  {
    echo "| Dependency | Version |"
    echo "|---|---|"
    echo "| supabase-kt (\`auth-kt\`, \`compose-auth\`, \`compose-auth-ui\`) | \`$(lib_dep_version supabase)\` |"
    echo "| Koin | \`$(lib_dep_version koin)\` |"
    echo "| Kotlin | \`$(lib_dep_version kotlin)\` |"
    echo "| Compose Multiplatform | \`$(lib_dep_version compose-multiplatform)\` |"
  } > "$TMP/deps.md"
}

gen_api_surface() {
  {
    for m in $(lib_modules); do
      echo "**\`$m\`**"
      echo
      local types
      types="$(lib_api_types "$m")"
      if [ -z "$types" ]; then
        echo "_No committed BCV baseline yet — run \`./gradlew :$m:apiDump\`._"
      else
        echo "$types" | sed 's/^/- `/;s/$/`/'
      fi
      echo
    done
  } > "$TMP/api.md"
}

gen_module_targets() {
  local m="$1"
  {
    echo "This module ships **$(lib_target_count "$m") targets**:"
    echo
    lib_targets "$m" | sed 's/^/- `/;s/$/`/'
  } > "$TMP/module-targets-$m.md"
}

# ── apply ────────────────────────────────────────────────────────────────────────────────────

gen_modules_table; gen_install; gen_targets_table; gen_deps_table; gen_api_surface

# `--check` runs the generator against a COPY and diffs. It must never touch the working tree:
# an earlier version wrote in place and then `git checkout --` to undo, which silently destroyed
# every uncommitted edit in those files. A verifier that can lose work is not a verifier.
if [ "$CHECK" = "1" ]; then
  WORK="$TMP/worktree"
  mkdir -p "$WORK"
  # Copy only what the generator writes to, plus what it reads to derive.
  cp -R README.md TARGET_MATRIX.md docs cmp-supabase-auth cmp-supabase-auth-compose \
        gradle gradle.properties settings.gradle.kts scripts "$WORK/"
  ( cd "$WORK" && bash scripts/docs/refresh.sh >/dev/null )
  if ! diff -rq --exclude=build --exclude=.gradle \
        README.md "$WORK/README.md" >/dev/null 2>&1 \
     || ! diff -rq TARGET_MATRIX.md "$WORK/TARGET_MATRIX.md" >/dev/null 2>&1 \
     || ! diff -rq docs "$WORK/docs" >/dev/null 2>&1 \
     || ! diff -rq --exclude=build cmp-supabase-auth "$WORK/cmp-supabase-auth" >/dev/null 2>&1 \
     || ! diff -rq --exclude=build cmp-supabase-auth-compose "$WORK/cmp-supabase-auth-compose" >/dev/null 2>&1
  then
    echo ""
    echo "❌ Generated docs are out of step with the build:"
    diff -ru --exclude=build --exclude=.gradle README.md "$WORK/README.md" 2>/dev/null | head -40 || true
    diff -ru TARGET_MATRIX.md "$WORK/TARGET_MATRIX.md" 2>/dev/null | head -40 || true
    diff -rq --exclude=build docs "$WORK/docs" 2>/dev/null || true
    echo ""
    echo "Fix with:"
    echo "    bash scripts/docs/refresh.sh"
    echo ""
    exit 1
  fi
  echo "✅ generated docs match the build"
  exit 0
fi

lib_replace_block README.md            "docs-gen:modules"   "$TMP/modules.md"
lib_replace_block README.md            "docs-gen:install"   "$TMP/install.md"
lib_replace_block README.md            "docs-gen:deps"      "$TMP/deps.md"
lib_replace_block TARGET_MATRIX.md     "docs-gen:targets"   "$TMP/targets.md"
lib_replace_block docs/api-reference.md "docs-gen:api"      "$TMP/api.md"
lib_replace_block docs/getting-started.md "docs-gen:install" "$TMP/install.md"
lib_replace_block docs/home.md         "docs-gen:install"   "$TMP/install.md"

for m in $(lib_modules); do
  gen_module_targets "$m"
  lib_replace_block "$m/README.md" "docs-gen:targets" "$TMP/module-targets-$m.md"
done

echo "✅ docs refreshed from the build (version $VERSION)"
