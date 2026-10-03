#!/usr/bin/env bash
# assert-publish-tags.sh — every version that reaches Maven Central must get a git tag + release.
#
# WHY THIS EXISTS. Maven Central is IMMUTABLE. A version published without a tag can never be
# re-cut, and the failure is invisible at publish time: every job stays green, the artifact lands,
# and only `gh release list` later shows the gap. Observed on v0.2.0 (run 36910691538, dispatched
# 2026-10-01): both modules reached Central while the release list still ended at v0.1.3.
#
# The input that decides this is `create-github-release` in publish.yml. Three forms have existed:
#   false                                    → NOTHING is ever tagged except the `release:` path
#   == 'workflow_dispatch'                   → an ALLOWLIST: correct for today's two triggers,
#                                              silently wrong for any third (schedule,
#                                              repository_dispatch, workflow_call, a re-enabled
#                                              push path) — each evaluates false and burns a version
#   != 'release'                             → a DENYLIST of the one event for which creating a
#                                              release is genuinely wrong (it started the run)
#
# Only the third form is safe by default, so that is what this asserts. An allowlist has to be
# edited in lockstep with every trigger change; a denylist of the single incompatible event does not.
#
# PT-1 the input is present at all
# PT-2 it is not a literal false
# PT-3 it is not an event-name allowlist (`== '<event>'`)
# PT-4 it is the denylist form, or an unconditional true
#
# exit 0 = PASS · 1 = FAIL (blocks). Pure bash + grep; no YAML parser needed.
set -uo pipefail
cd "$(dirname "$0")/../.."

WF=".github/workflows/publish.yml"
[ -f "$WF" ] || { echo "SKIP: no $WF — nothing to guard"; exit 0; }

LINE="$(grep -E '^[[:space:]]*create-github-release:' "$WF" | head -1)"
VAL="${LINE#*create-github-release:}"
VAL="$(printf '%s' "$VAL" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

fail=0
if [ -z "$LINE" ]; then
  echo "FAIL PT-1: $WF declares no 'create-github-release:' input."
  echo "       Without it the reusable workflow's create-release job is skipped and every"
  echo "       published version ships untagged."
  fail=1
else
  echo "  PT-1 ok: create-github-release is declared"
  case "$VAL" in
    false|'${{ false }}')
      echo "FAIL PT-2: create-github-release is a literal false."
      echo "       Only the 'release:' trigger would ever be tagged; a workflow_dispatch publish"
      echo "       reaches immutable Central with no tag, no release and no notes."
      fail=1 ;;
    *"=="*)
      echo "FAIL PT-3: create-github-release uses an event-name ALLOWLIST: $VAL"
      echo "       An allowlist must be edited in lockstep with every new trigger, and anything"
      echo "       not listed publishes untagged. Express the ONE incompatible event instead:"
      echo "         create-github-release: \${{ github.event_name != 'release' }}"
      fail=1 ;;
    *"!= 'release'"*|*'!= "release"'*|true|'${{ true }}')
      echo "  PT-2 ok: not a literal false"
      echo "  PT-3 ok: not an event allowlist"
      echo "  PT-4 ok: fail-safe form — $VAL" ;;
    *)
      echo "FAIL PT-4: unrecognised create-github-release expression: $VAL"
      echo "       Expected \${{ github.event_name != 'release' }} (every entry point except the"
      echo "       release trigger creates the tag) or an unconditional true."
      fail=1 ;;
  esac
fi

if [ "$fail" -eq 0 ]; then
  echo "publish-tags gate: every Maven publish path creates a tag + GitHub release"
fi
exit "$fail"
