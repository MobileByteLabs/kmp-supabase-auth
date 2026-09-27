# Target Matrix — single source of truth

Which Kotlin Multiplatform targets each module ships, and **why** a module ships fewer.

**Upstream reference:** <https://kotlinlang.org/docs/native-target-support.html> — JetBrains' tier
list is authoritative for what Kotlin/Native supports. This document records what *this library*
ships against it. When the two disagree, the upstream page wins and this file is stale.

> Every figure below was **measured** on 2026-09-26 by fetching the per-target POM from Maven
> Central. A 404 is authoritative absence. Re-measure rather than trust these after any dependency
> bump — the command is at the bottom.

---

## 1. This library's modules

<!-- docs-gen:targets:begin -->
| Module | Count | Targets |
|---|---|---|
| `cmp-supabase-auth` | **17** | `android` `iosArm64` `iosSimulatorArm64` `iosX64` `js` `jvm` `linuxX64` `macosArm64` `macosX64` `mingwX64` `tvosArm64` `tvosSimulatorArm64` `tvosX64` `wasmJs` `watchosArm64` `watchosSimulatorArm64` `watchosX64`  |
| `cmp-supabase-auth-compose` | **6** | `android` `iosArm64` `iosSimulatorArm64` `js` `jvm` `wasmJs`  |
<!-- docs-gen:targets:end -->


`sample-app` is not published and is excluded from `module-pattern: 'cmp-'` in CI.

---

## 2. Measured dependency publication sets

| Artifact | Version | Targets | Missing |
|---|---|---|---|
| `auth-kt` | 3.8.0 | 17 | `linuxArm64` `watchosArm32` `watchosDeviceArm64` `wasmWasi` |
| `compose-auth` | 3.8.0 | 7 | **all macOS** |
| `compose-auth-ui` | 3.8.0 | 7 | **all macOS** |
| Compose Multiplatform | 1.12.0 | 7 | **`iosX64`**, `macosX64` |

**`auth-kt` is the binding constraint on `cmp-supabase-auth`** — it publishes 17 of the 21 targets
this family considers reachable, missing `linuxArm64`, `watchosArm32`, `watchosDeviceArm64` and
`wasmWasi`. Koin publishes every remaining target and adds no constraint.

**Store5 was removed.** It had capped this module at 8 targets, and it was redundant: `auth-kt`
already owns session lifecycle, persistence and refresh, so a second cache layer would have been
a second owner of the same state. See `AuthSessionStore`'s KDoc.

---

## 3. Two consequences worth stating plainly

**No native Apple sign-in on macOS.** `compose-auth` ships no macOS artifact, so `cmp-supabase-auth-compose`
cannot reach macOS at all. macOS consumers take `cmp-supabase-auth` and the web-OAuth fallback. This is
documented in [docs/SETUP_APPLE.md](docs/SETUP_APPLE.md) rather than left to surface at link time.

**`cmp-supabase-auth` reaches `iosX64` but `cmp-supabase-auth-compose` does not.** Different causes: `compose-auth`
*does* publish `iosX64`, but Compose Multiplatform does not. A Compose-bearing module can never
match its headless sibling's matrix anyway — the Compose compiler plugin applies to every
compilation in a module and fails on any target lacking the runtime. Confining Compose to an
intermediate source set does not work; that is why the library ships as a headless/`-compose` pair.

---

## 4. When a dependency blocks a target

**Normally: do not drop the target — confine the dependency** to an intermediate source set, so
the rest of the module still ships everywhere.

That rule does **not** apply when the dependency *is* the module's reason to exist, which is the
case for `auth-kt` in `cmp-supabase-auth`: there is no meaningful auth module without a GoTrue
client, so a target that cannot resolve one has nothing to receive.

---

## 5. Re-measuring

```bash
V=3.8.0
for a in auth-kt-linuxarm64 auth-kt-watchosarm32 auth-kt-watchosdevicearm64 auth-kt-macosarm64; do
  printf '%s -> %s\n' "$a" \
    "$(curl -s -o /dev/null -w '%{http_code}' \
        "https://repo1.maven.org/maven2/io/github/jan-tennert/supabase/$a/$V/$a-$V.pom")"
done
```

200 = published, 404 = absent. The gradle cache is **not** a substitute: it only proves what has
been resolved locally, so absence there means nothing.
