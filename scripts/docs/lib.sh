#!/usr/bin/env bash
# scripts/docs/lib.sh — shared derivation helpers. Sourced, never run directly.
#
# Every function here answers ONE question from the build, not from prose. That is the whole
# point: the facts that went stale in this repo — target counts, the version, the module list,
# the public API — are all already stated authoritatively in build files. Restating them by hand
# is what let the docs claim 8 targets and a Store5 dependency weeks after both had changed.

set -euo pipefail

# The one version source. Every module reads the same property.
lib_version() {
  grep -E '^supabaseauth\.version=' gradle.properties | cut -d= -f2
}

# Published modules, in settings order. `sample-app` is excluded deliberately: it is proof, not
# an artifact, and CI's `module-pattern: 'cmp-'` excludes it too.
lib_modules() {
  grep -oE '^include\(":cmp-[a-z-]+"\)' settings.gradle.kts | sed -e 's/^include("://' -e 's/")$//'
}

# Targets a module DECLARES, read from its build file rather than assumed.
#
# Handles both shapes this repo uses: bare `iosX64()` calls and the `listOf(iosX64(), …)` block
# that groups Apple targets for the Swift linker workaround. Comments are stripped first, so a
# target mentioned in a KDoc note is never counted as declared — that distinction matters here,
# because these build files explain at length which targets are ABSENT and why.
lib_targets() {
  local module="$1" build="$1/build.gradle.kts"
  [ -f "$build" ] || return 0
  sed -e 's://.*::' "$build" \
    | grep -oE '\b(jvm|androidLibrary|js|wasmJs|wasmWasi|iosX64|iosArm64|iosSimulatorArm64|macosX64|macosArm64|tvosX64|tvosArm64|tvosSimulatorArm64|watchosX64|watchosArm32|watchosArm64|watchosSimulatorArm64|watchosDeviceArm64|linuxX64|linuxArm64|mingwX64)\s*[({]' \
    | sed -e 's/[({]$//' -e 's/[[:space:]]*$//' \
    | sed -e 's/^androidLibrary$/android/' \
    | sort -u
}

lib_target_count() { lib_targets "$1" | wc -l | tr -d ' '; }

# Public API types from the committed BCV baseline — the same artifact `apiCheck` gates on, so
# the docs cannot claim a surface the build does not actually publish.
lib_api_types() {
  local module="$1" api
  api="$(find "$module/api" -name '*.api' 2>/dev/null | head -1)"
  [ -n "$api" ] || return 0
  # Python rather than grep: BCV writes JVM descriptors (`io/github/.../AuthError$Cancelled`),
  # and matching `$`-nested names in an ERE is where the previous attempt produced
  # "empty (sub)expression". Nested classes are folded into their outer type — a reader wants
  # "AuthError", not five sealed subclasses.
  python3 - "$api" <<'PYAPI'
import re, sys
types = set()
for line in open(sys.argv[1], encoding="utf-8"):
    # BCV writes modifiers in a fixed order but a VARYING set:
    #   public final class …        public abstract interface class …
    # Matching "(?:class|interface)" captured the literal word "class" out of
    # "interface class" and dropped every interface. Anchor on the LAST "class ".
    m = re.match(r"public .*?\bclass (\S+)", line)
    if not m:
        continue
    name = m.group(1).split("/")[-1].split("$")[0]
    if name.endswith("Kt") or name.startswith("ComposableSingletons"):
        continue
    types.add(name)
for t in sorted(types):
    print(t)
PYAPI
}

lib_dep_version() {
  grep -E "^$1 = \"" gradle/libs.versions.toml | head -1 | cut -d'"' -f2
}

# Replace the content between `<!-- {marker}:begin -->` and `<!-- {marker}:end -->`.
# Prose outside the markers is preserved byte-for-byte — that property is what makes running
# this safe on every push.
lib_replace_block() {
  local file="$1" marker="$2" content_file="$3"
  # A MISSING MARKER IS A HARD FAIL, never a skip. Skipping is how `--check` once reported
  # "✅ generated docs match the build" across nine files it had not written a single byte to —
  # green for work it never ran. If a block is genuinely not wanted in a file, remove the call
  # in refresh.sh; do not let the file quietly opt out.
  grep -q "<!-- ${marker}:begin -->" "$file" || {
    echo "❌ ${file}: no '<!-- ${marker}:begin -->' marker — add it, or drop the call in refresh.sh" >&2
    return 1
  }
  python3 - "$file" "$marker" "$content_file" <<'PY'
import io, re, sys
path, marker, content_path = sys.argv[1], sys.argv[2], sys.argv[3]
body = io.open(path, encoding="utf-8").read()
new = io.open(content_path, encoding="utf-8").read().rstrip("\n")
# Match the markers and EVERYTHING between them, including nothing at all — a freshly
# inserted, still-empty block has no newline between begin and end, and requiring one made
# the first run report "markers are malformed" on every file.
pattern = re.compile(
    r"<!-- %s:begin -->.*?<!-- %s:end -->" % (re.escape(marker), re.escape(marker)),
    re.S,
)
if not pattern.search(body):
    sys.exit(f"{path}: '{marker}' begin/end markers are malformed")
replacement = f"<!-- {marker}:begin -->\n{new}\n<!-- {marker}:end -->"
body = pattern.sub(lambda _m: replacement, body)
io.open(path, "w", encoding="utf-8").write(body)
PY
}
