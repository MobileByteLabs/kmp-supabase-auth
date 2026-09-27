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
    echo "| Module | Artifact | Targets | Latest |"
    echo "|---|---|---|---|"
    for m in $(lib_modules); do
      # Coordinates WITHOUT a version, plus the live badge — the same shape kmp-toolkit uses.
      echo "| [\`$m\`]($m/README.md) | \`io.github.mobilebytelabs:$m\` | $(lib_target_count "$m") | [![](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/$m?label=%20)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/$m) |"
    done
    echo "| \`sample-app\` | — (not published) | — | — |"
  } > "$TMP/modules.md"
}

# NEVER writes a literal version into an install snippet.
#
# A pinned number in prose goes stale the moment the next release lands, and a reader who copies
# it silently gets an old library. kmp-toolkit's README contains ZERO occurrences of its own
# version for exactly this reason: the live Maven Central badge is the version source of truth,
# because it is read from the registry at page load and can never be stale.
#
# So the snippet declares the version ONCE as a variable and points at the badge for its value.
gen_install() {
  {
    echo 'Set `supabaseAuthVersion` to the version shown by the **Maven Central** badge above —'
    echo 'that badge is read live from the registry and is always the latest published release.'
    echo
    echo '```kotlin'
    echo 'val supabaseAuthVersion = "<see the Maven Central badge>"'
    echo
    echo 'dependencies {'
    for m in $(lib_modules); do
      case "$m" in
        *-compose) note="  // Compose UI (optional)" ;;
        *)         note="  // headless" ;;
      esac
      echo "    implementation(\"io.github.mobilebytelabs:$m:\$supabaseAuthVersion\")$note"
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

# Badges. The VERSION badge is generated from gradle.properties — the same property every
# module builds against — so a version bump cannot leave a stale number in a badge. The Maven
# Central badge is live from the registry and will read "not found" until the first publish;
# that is honest rather than misleading.
gen_badges() {
  local scope="${1:-root}" module="${2:-cmp-supabase-auth}"
  # LICENSE lives at the REPO ROOT, so a module README one level down needs `../`.
  local up=""
  [ "$scope" = "module" ] && up="../"
  # The version badge links to gradle.properties on GitHub, ABSOLUTELY — not relatively. The
  # file is the version SoT but it is not staged into the docs site, so a relative link 404s
  # there while resolving fine in the repo view. An absolute blob URL works from both.
  {
    printf '[![Maven Central](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/%s?label=maven%%20central)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/%s)\n' "$module" "$module"
    printf '[![Kotlin](https://img.shields.io/badge/Kotlin-%s-blue.svg?logo=kotlin)](https://kotlinlang.org)\n' "$(lib_dep_version kotlin)"
    if [ "$scope" = "root" ] || [ "$module" = "cmp-supabase-auth-compose" ]; then
      printf '[![Compose Multiplatform](https://img.shields.io/badge/Compose-%s-blue.svg)](https://www.jetbrains.com/compose-multiplatform/)\n' "$(lib_dep_version compose-multiplatform)"
    fi
    printf '[![License](https://img.shields.io/badge/License-Apache%%202.0-green.svg)](%sLICENSE)\n' "$up"
  } > "$TMP/badges-$scope-$module.md"
}

# Emits a LIVE badge, never the number. Any surface that wants to state "the current version"
# gets something the registry answers for; nothing in the docs freezes a version string.
# YAML frontmatter cannot hold a markdown badge, and the coherence workflow only requires the
# `version` FIELD to exist, not to carry a number. So the field states where the real answer
# lives instead of freezing a copy of it.
gen_version_field() {
  printf 'see Maven Central badge in README' > "$TMP/version-field.md"
}

gen_version() {
  printf '[![Maven Central](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/cmp-supabase-auth?label=latest&color=3ecf8e)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/cmp-supabase-auth)' \
    > "$TMP/version.md"
}

gen_module_install() {
  local m="$1"
  {
    echo 'Version: see the **Maven Central** badge above (live from the registry).'
    echo
    echo '```kotlin'
    echo "    implementation(\"io.github.mobilebytelabs:$m:\$supabaseAuthVersion\")"
    echo '```'
  } > "$TMP/module-install-$m.md"
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
gen_version; gen_version_field; gen_badges root cmp-supabase-auth

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
lib_replace_block README.md            "docs-gen:badges"    "$TMP/badges-root-cmp-supabase-auth.md"
lib_replace_inline docs/_coverpage.md  "docs-gen:version"   "$TMP/version.md"

for m in $(lib_modules); do
  gen_module_targets "$m"
  gen_module_install "$m"
  gen_badges "module" "$m"
  lib_replace_block "$m/README.md" "docs-gen:targets" "$TMP/module-targets-$m.md"
  lib_replace_block "$m/README.md" "docs-gen:install" "$TMP/module-install-$m.md"
  lib_replace_block "$m/README.md" "docs-gen:badges"  "$TMP/badges-module-$m.md"
  # DEVELOPMENT.md frontmatter carries the version too; the coherence workflow reads it.
  lib_replace_inline "$m/DEVELOPMENT.md" "docs-gen:version" "$TMP/version-field.md"
done

echo "✅ docs refreshed from the build (version $VERSION)"
