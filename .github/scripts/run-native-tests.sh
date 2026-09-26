#!/usr/bin/env bash
# Run a Kotlin/Native test task across the auth-* LIBRARY modules, skipping the sample apps.
#
# WHY THE SAMPLES ARE EXCLUDED
# `./gradlew iosSimulatorArm64Test` from the root runs that task in EVERY project declaring the
# target, including the `samples/*/composeApp` Compose apps. Measured on run 34639024914:
# 770 of 1662 graphed tasks (46%) were `:samples:*`, each app dragging its own SwiftPM synthetic-
# package resolution (fetchSyntheticImportProjectPackages, dumpXcodebuildArgsIphonesimulator,
# linkDebugTestIosSimulatorArm64) through the slow Firebase path. The iOS job hit its 45-minute
# timeout mid-link on the LAST sample and was cancelled — the library tests had already run; the
# remainder of the budget went to samples.
#
# What that bought: five `ComposeAppCommonTest.example` cases, every one the `assertEquals(3, 1 + 2)`
# boilerplate the KMP wizard emits. Not one line of library behaviour.
#
# This workflow exists to execute `commonTest` on native targets, where an assertion can be wrong
# while passing on JVM/JS (the 3.5.23 Apple incident). That is a claim about the cmp-* modules.
# Whether the samples still COMPILE and LINK for iOS is real coverage, but it belongs to
# compile-all-targets, which runs them on every push (15m54s, green).
#
# Verified with `--dry-run` on iosSimulatorArm64Test: 1662 -> 876 graphed tasks, samples 770 -> 11
# (those 11 being a SKIPPED lock-file metadata task), and all 22 `:cmp-*:iosSimulatorArm64Test`
# tasks byte-identical to the unpruned graph — no library test is lost.
#
# WHY EACH SAMPLE IS TARGET-CHECKED FIRST
# `-x` on a task path that does not exist is a hard configuration failure ("Cannot locate excluded
# tasks that match ..."), not a no-op. The samples declare ONLY iosArm64 + iosSimulatorArm64, so
# blanket exclusion would break the watchOS, Linux and Windows jobs — measured: 0 sample tasks in
# the linuxX64Test and mingwX64Test graphs. A sample is therefore excluded only when its own build
# file declares the target this task belongs to; otherwise the invocation is left untouched, which
# is exactly today's (correct) behaviour for those jobs.
#
# This script invokes Gradle itself rather than emitting flags for the caller to interpolate:
# `./gradlew $(emit-flags)` depends on word-splitting the substitution, which produced ONE argument
# on a shell whose IFS lacks a space, and Gradle then failed with "Empty segments" on a task path
# containing the entire flag list. An array passed directly cannot misbehave that way.
#
# Usage: run-native-tests.sh <task-name> [extra gradle args...]
#   e.g. run-native-tests.sh iosSimulatorArm64Test --continue
set -euo pipefail

TASK="${1:?usage: run-native-tests.sh <task-name> [extra gradle args...]}"
shift

SETTINGS="settings.gradle.kts"
[ -f "$SETTINGS" ] || { echo "::error::$SETTINGS not found — run from the repo root." >&2; exit 1; }

# `iosSimulatorArm64Test` -> `iosSimulatorArm64`, the target function a build file would call.
TARGET="${TASK%Test}"

EXCLUDED=0
SKIPPED=0
ARGS=()
while IFS= read -r project; do
  [ -n "$project" ] || continue

  # `:samples:sample-x:composeApp` -> `samples/sample-x/composeApp/build.gradle.kts`
  dir="$(printf '%s' "${project#:}" | tr ':' '/')"
  build_file="${dir}/build.gradle.kts"

  if [ ! -f "$build_file" ]; then
    echo "  note: ${project} has no build.gradle.kts — not excluding."
    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  # Strip `//` line comments and single-line `/* */` before matching, so a commented-out target
  # declaration cannot cause a bogus exclusion (and thus a hard "cannot locate" failure).
  if sed -e 's://.*::' -e 's:/\*[^*]*\*/::g' "$build_file" | grep -q "${TARGET}("; then
    ARGS+=("-x" "${project}:${TASK}")
    EXCLUDED=$((EXCLUDED + 1))
  else
    SKIPPED=$((SKIPPED + 1))
  fi
# This repo's samples are TOP-LEVEL (`include(":sample-app")`), not nested under `:samples:`
# the way kmp-toolkit's are. Matching only `:samples:` here would silently exclude NOTHING and
# run native tests against the sample app — which is precisely what this script exists to avoid.
done < <(grep -oE 'include\("(:samples:[^"]*|:sample-app)"\)' "$SETTINGS" | sed -e 's/^include("//' -e 's/")$//')

if [ "$EXCLUDED" -gt 0 ]; then
  echo "Running ${TASK} across the auth-* modules — excluding ${EXCLUDED} sample app(s) that declare ${TARGET}."
else
  echo "Running ${TASK}: no sample app declares ${TARGET} (${SKIPPED} checked), so nothing to exclude."
fi

# ${ARGS[@]+...} guards the empty-array-under-`set -u` case, which bash 3.2 (the macOS runner
# shell) treats as an unbound variable rather than an empty expansion.
exec ./gradlew "$TASK" ${ARGS[@]+"${ARGS[@]}"} "$@"
