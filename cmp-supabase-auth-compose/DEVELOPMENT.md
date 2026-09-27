---
module: cmp-supabase-auth-compose
artifact: io.github.mobilebytelabs:cmp-supabase-auth-compose
version: <!-- docs-gen:version:begin -->0.1.1<!-- docs-gen:version:end -->
package: io.github.mobilebytelabs.supabaseauth.compose
api_tier: experimental
last_reviewed: 2026-09-26
goal_plan_ref: plan-layer/project-plans/mbs/kmp-toolkit/active/cmp-supabase-auth/RESEARCH.md
adr_refs: []
---

# cmp-supabase-auth-compose — Development

Compose Multiplatform half of KMP Supabase Auth. Owns the `remember*` sign-in wrappers,
brand-compliant provider buttons, the drop-in login screen and the ViewModel. Depends on
[cmp-supabase-auth](../cmp-supabase-auth/DEVELOPMENT.md), which owns everything headless.

## §1 Module Identity

| Field | Value |
|---|---|
| Artifact | `io.github.mobilebytelabs:cmp-supabase-auth-compose` |
| Package | `io.github.mobilebytelabs.supabaseauth.compose` |
| Targets | **6** — see [TARGET_MATRIX.md](../TARGET_MATRIX.md) |
| API tier | experimental (pre-1.0; BCV baselines committed under `api/`) |

## §2 Per-Platform Parity Matrix

| Target | Status | Notes |
|---|---|---|
| android | ✅ | Native Google sign-in via Credential Manager |
| jvm | ✅ | Desktop; system-browser OAuth redirect |
| js / wasmJs | ✅ | GoTrue OAuth redirect |
| iosArm64 / iosSimulatorArm64 | ✅ | Native Apple sign-in via ASAuthorization |
| iosX64 | ❌ | Compose Multiplatform 1.11.0 publishes no iosX64 artifact |
| macOS | ❌ | `compose-auth` publishes **no macOS artifact at all** — so there is **no native Apple sign-in on macOS**; macOS consumers take cmp-supabase-auth plus the web-OAuth fallback |

Two targets fewer than cmp-supabase-auth, for two DIFFERENT reasons. Both measured, neither a preference.

## §3 Public API Surface

Generated from `api/*.api` by Binary Compatibility Validator. Regenerate with:

```bash
./gradlew :cmp-supabase-auth-compose:apiDump
```

Current surface: empty — the UI lands on top of cmp-supabase-auth's client and repository.

## §4 Spec Snapshot

**Success comes from the session stream, never the provider callback.** On Android the native
Google `onResult(Success)` callback frequently never fires even though the ID-token exchange
succeeded and the session landed — verified on-device in the `cappy` app, which sat on
"Signing in…" while Supabase logged `Authenticated`. The ViewModel therefore derives signed-in
from `AuthRepository.isSignedIn`; `onResult` is kept only for Error / NetworkError /
ClosedByUser, which do fire reliably.

Provider marks come from `compose-auth-ui`'s `ProviderButtonContent`, not hand-drawn icons:
Apple and Google both publish brand requirements, and a bespoke mark risks store review.

## §5 Extension Recipes

`SupabaseLoginScreen` is slot-based — pass `header` / `footer` composables to brand it without
forking the screen. For full control, compose your own screen from `GoogleSignInButton`,
`AppleSignInButton` and `SupabaseAuthViewModel`.

## §6 Active Development Log

| Date | Change |
|---|---|
| 2026-09-26 | Module created; build + publishing + BCV wired. UI implementation pending |

## §7 Cross-Platform Parity Recipes

A Compose-bearing module can never match its headless sibling's matrix: the Compose compiler
plugin applies to EVERY compilation in a module and fails on any target lacking the runtime.
Confining Compose to an intermediate source set does not work — it was tried and reverted.

## §8 Related

- [README.md](README.md) — consumer-facing docs
- [../TARGET_MATRIX.md](../TARGET_MATRIX.md) — target policy, measured
- [../docs/SETUP_GOOGLE.md](../docs/SETUP_GOOGLE.md) · [../docs/SETUP_APPLE.md](../docs/SETUP_APPLE.md)
- [../cmp-supabase-auth/DEVELOPMENT.md](../cmp-supabase-auth/DEVELOPMENT.md)

## §9 Observability Surface

**None today.** kmp-toolkit's `cmp-observe` hooks were deliberately not adopted: its published
jvm artifact is compiled at Java 21 (class file 65), which would force JDK 21 on every desktop
consumer of this library. Revisit if a lower-target jvm artifact ships.
