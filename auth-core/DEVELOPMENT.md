---
module: auth-core
artifact: io.github.mobilebytelabs:auth-core
version: 0.1.0
package: io.github.mobilebytelabs.supabaseauth
api_tier: experimental
last_reviewed: 2026-09-26
goal_plan_ref: plan-layer/project-plans/mbs/kmp-toolkit/active/cmp-supabase-auth/RESEARCH.md
adr_refs: []
---

# auth-core — Development

Headless half of KMP Supabase Auth. Owns configuration, the Supabase client boundary, the
Store5-backed session store, the repository, and the Koin wiring. No Compose — see
[auth-compose](../auth-compose/DEVELOPMENT.md) for the UI half.

## §1 Module Identity

| Field | Value |
|---|---|
| Artifact | `io.github.mobilebytelabs:auth-core` |
| Package | `io.github.mobilebytelabs.supabaseauth` |
| Targets | **8** — see [TARGET_MATRIX.md](../TARGET_MATRIX.md) |
| API tier | experimental (pre-1.0; BCV baselines committed under `api/`) |

## §2 Per-Platform Parity Matrix

| Target | Status | Notes |
|---|---|---|
| android | ✅ | Native Google via Credential Manager (in auth-compose) |
| jvm | ✅ | Desktop; system-browser OAuth redirect |
| js / wasmJs | ✅ | GoTrue OAuth redirect |
| iosArm64 / iosSimulatorArm64 / iosX64 | ✅ | Native Apple available via auth-compose (arm64 + simulator only) |
| linuxX64 | ✅ | OAuth redirect |
| macOS / tvOS / watchOS / mingwX64 / linuxArm64 | ❌ | `store5` 5.1.0-beta01 publishes no artifact |

Dropped targets are a **measurement**, not a preference. Re-probe Maven Central before widening.

## §3 Public API Surface

Generated from `api/*.api` by Binary Compatibility Validator. Regenerate with:

```bash
./gradlew :auth-core:apiDump
```

Current surface: `SupabaseAuthConfig`, `SupabaseAuth`.

## §4 Spec Snapshot

The library installs Supabase `Auth` + `ComposeAuth` onto the consumer's **existing** client
through kmp-project-template's `SupabaseExtrasProvider` seam. It never calls
`createSupabaseClient`. A second client carries no session, so every RLS-gated call resolves no
`auth.uid()` — while compiling cleanly and passing static checks the whole way.

Ownership boundary: this library owns the **session**; the consuming app keeps owning user
**profile** data in its own `UserDataStore` / `UserPreferencesRepository`. `AuthUser` carries
identity only.

## §5 Extension Recipes

Every binding is `single<Interface>`, so an app overrides any rung by declaring its own after
the include:

```kotlin
includes(supabaseAuthNetwork(config) {
    extraInstall { install(Realtime) }   // same client — never build a second one
    userMapper { raw -> raw.copy(displayName = raw.displayName ?: "Anonymous") }
    onSessionChanged { session -> analytics.setUserId(session?.id) }
})
```

## §6 Active Development Log

| Date | Change |
|---|---|
| 2026-09-26 | Module created from `mbl-library-template-kmp`; CI/quality stack ported from kmp-toolkit; `SupabaseAuthConfig` + `SupabaseAuth.validate` landed |

## §7 Cross-Platform Parity Recipes

When a dependency blocks a target, confine it to an intermediate source set rather than dropping
the target — unless the dependency *is* the module's reason to exist, as `store5` is here.

## §8 Related

- [README.md](README.md) — consumer-facing docs
- [../TARGET_MATRIX.md](../TARGET_MATRIX.md) — target policy, measured
- [../docs/SETUP_GOOGLE.md](../docs/SETUP_GOOGLE.md) · [../docs/SETUP_APPLE.md](../docs/SETUP_APPLE.md)
- [../auth-compose/DEVELOPMENT.md](../auth-compose/DEVELOPMENT.md)

## §9 Observability Surface

**None today.** kmp-toolkit's `cmp-observe` hooks were deliberately not adopted: its published
jvm artifact is compiled at Java 21 (class file 65), which would force JDK 21 on every desktop
consumer of this library. Revisit if a lower-target jvm artifact ships.
