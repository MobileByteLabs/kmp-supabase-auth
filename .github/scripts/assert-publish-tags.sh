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
# PT-5 `bump-after-release: true` AND `next-bump-type` set — the PAIR, not just the flag
#
# PT-5 guards the OTHER half of "a publish must not leave the repo mid-release". The job that opens
# it was wired and switched off, reporting `skipped` on a successful release — which from the
# outside is indistinguishable from not existing. With the version left at the just-released number,
# the next publish either re-cuts a duplicate (Central rejects it: "Component with package url …
# already exists", observed on run 37115504768) or relies on someone remembering the bump by hand.
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

# PT-5 — the post-release version bump must be enabled.
BUMP="$(grep -E '^[[:space:]]*bump-after-release:' "$WF" | head -1)"
BVAL="${BUMP#*bump-after-release:}"
BVAL="$(printf '%s' "$BVAL" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
if [ -z "$BUMP" ]; then
  echo "FAIL PT-5: $WF declares no 'bump-after-release:' input."
  echo "       The reusable workflow then defaults it, and a publish can leave the version"
  echo "       unchanged — so the next release re-cuts a version Central rejects as a duplicate."
  fail=1
elif [ "$BVAL" != "true" ]; then
  echo "FAIL PT-5: bump-after-release is '$BVAL', not true."
  echo "       The 'Open Bump PR (next cycle)' job reports 'skipped' in that state, which reads"
  echo "       as 'not implemented' while the version silently stays at the released number."
  echo "       Set: bump-after-release: true"
  fail=1
else
  # The flag alone is a HALF-configuration: it says THAT the version advances, not BY WHAT.
  # kmp-toolkit, the working reference in this org, sets both (its publish.yml:54-55). Asserting
  # only the flag is how this gate passed while the bump was still incompletely wired.
  NBT="$(grep -E '^[[:space:]]*next-bump-type:' "$WF" | head -1)"
  NVAL="${NBT#*next-bump-type:}"
  NVAL="$(printf '%s' "$NVAL" | sed "s/^[[:space:]]*//;s/[[:space:]]*$//;s/^'//;s/'$//" )"
  case "$NVAL" in
    patch|minor|major)
      echo "  PT-5 ok: bump-after-release: true + next-bump-type: $NVAL" ;;
    "")
      echo "FAIL PT-5: bump-after-release is true but 'next-bump-type' is not set."
      echo "       The flag says THAT the version advances; next-bump-type says BY WHAT. Match the"
      echo "       org reference (kmp-toolkit publish.yml): next-bump-type: 'patch'"
      fail=1 ;;
    *)
      echo "FAIL PT-5: next-bump-type is '$NVAL' — expected patch | minor | major."
      fail=1 ;;
  esac
fi

if [ "$fail" -eq 0 ]; then
  echo "publish-tags gate: every Maven publish creates a tag + GitHub release AND advances the version"
fi
exit "$fail"
