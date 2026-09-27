# cmp-supabase-auth

> **Target support:** see [TARGET_MATRIX.md](../TARGET_MATRIX.md) — the single source of truth for
> which KMP targets every module ships and why.

Headless half of **KMP Supabase Auth**. Configuration, the Supabase client boundary, a
session store, the repository, and the Koin wiring. No Compose — the UI lives in
[cmp-supabase-auth-compose](../cmp-supabase-auth-compose/README.md).

<!-- docs-gen:badges:begin -->
[![Version](https://img.shields.io/badge/version-0.1.1-3ecf8e.svg)](https://github.com/MobileByteLabs/kmp-supabase-auth/blob/dev/gradle.properties)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/cmp-supabase-auth?label=maven%20central)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/cmp-supabase-auth)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../LICENSE)
<!-- docs-gen:badges:end -->

## Install

<!-- docs-gen:install:begin -->
```kotlin
dependencies {
    implementation("io.github.mobilebytelabs:cmp-supabase-auth:0.1.1")
}
```
<!-- docs-gen:install:end -->

<!-- docs-gen:targets:begin -->
This module ships **17 targets**:

- `android`
- `iosArm64`
- `iosSimulatorArm64`
- `iosX64`
- `js`
- `jvm`
- `linuxX64`
- `macosArm64`
- `macosX64`
- `mingwX64`
- `tvosArm64`
- `tvosSimulatorArm64`
- `tvosX64`
- `wasmJs`
- `watchosArm64`
- `watchosSimulatorArm64`
- `watchosX64`
<!-- docs-gen:targets:end -->

## The one rule that matters

**This library never calls `createSupabaseClient`.** It installs `Auth` and `ComposeAuth` onto the
client your app already has, through kmp-project-template's `SupabaseExtrasProvider` seam.

Build a second client to get `Auth` and your generated API bindings will hold a *different*
instance carrying no session — so every RLS-gated call resolves no `auth.uid()`, while compiling
cleanly and passing static checks the whole way. It is a silent, runtime-only failure, which is
why the library removes the opportunity rather than documenting the hazard.

## Koin module

Two ergonomics, one implementation. `supabaseAuth(config)` is *defined as* the three per-rung
modules, so they cannot drift apart — a test asserts the binding sets are identical.

**One line:**

```kotlin
val AppModule = module {
    includes(
        supabaseAuth(
            SupabaseAuthConfig(
                projectRef = "your-project-ref",
                googleWebClientId = BuildKonfig.GOOGLE_OAUTH_WEB_CLIENT_ID,
                redirectUrl = "myapp://login-callback",
            ),
        ),
    )
}
```

**Or per rung**, placed in the layer each belongs to:

```kotlin
// core/network/di/ProjectNetworkModule.kt   ← owner:fork, survives template sync
includes(supabaseAuthNetwork(config))

// core/store/di/StoreModule.kt
includes(supabaseAuthStore())

// core/data/di/ProjectRepositoryModule.kt
includes(supabaseAuthRepository())
```

| Module function | Binds | Belongs in |
|---|---|---|
| `supabaseAuthNetwork(config)` | `SupabaseAuthClient`, `SupabaseAuthOptions` | `core/network` |
| `supabaseAuthStore()` | `AuthSessionStore` | `core/store` |
| `supabaseAuthRepository()` | `AuthRepository` | `core/data` |
| `supabaseAuth(config)` | all three | anywhere |

## Configuration

```kotlin
SupabaseAuthConfig(
    projectRef        = "abcdefgh",                      // required
    googleWebClientId = "123.apps.googleusercontent.com", // the WEB id — see below
    googleIosClientId = "",
    appleServiceId    = "",
    redirectUrl       = "myapp://login-callback",
)
```

**Use the Google Cloud *Web* client id** — not the Android one, not the iOS one. Both Supabase
GoTrue and the Android Credential Manager want the Web id. Supplying the Android id is the single
most common setup mistake and it fails at runtime with an opaque provider error. Full walkthrough:
[docs/SETUP_GOOGLE.md](../docs/SETUP_GOOGLE.md).

**Unconfigured is a supported state.** A blank `googleWebClientId` degrades to the OAuth-redirect
path rather than throwing, so an app part-way through console setup still builds and runs. Only a
blank or placeholder `projectRef` is fatal — without it there is no client to install onto.

```kotlin
SupabaseAuth.validate(config)   // throws only on a missing/placeholder projectRef
```

## Ownership boundary

| Owns | Who |
|---|---|
| The Supabase **session** — tokens, provider identity, status | **this library** |
| User **profile** data, app preferences | **your app**, untouched |
| Attaching the JWT to API calls | **your app's** existing `AuthHeaderBridge` |
| Logout fan-out | **your app's** `UserLogoutManager` / `StoreRegistry` |

`AuthUser` carries identity only. The library never writes to your preferences store and defines
no profile type, so there is never a second owner of state you already own.

## Session store

A plain `StateFlow` holder, deliberately **not** a Store5 store:

- **Memory only.** GoTrue already persists and refreshes the session. Caching it again here would
  let the app show a signed-in user after the real token had expired.
- **Not enrolled in the logout purge.** This store is what *tells* the app a logout happened;
  purging it would clear the very stream the app reads to notice the purge.
- **Push, not fetch.** GoTrue emits session changes, so there is no fetcher to write. Adding one
  creates a second read path for one piece of state.

Store5 was tried and removed — it capped this module at 8 targets and duplicated lifecycle
`auth-kt` already owns.

## Status

Pre-1.0 and implemented: the API below is shipped, tested and green on every declared target.
Not yet published to Maven Central, and native sign-in is unverified on a physical device.
See [DEVELOPMENT.md](DEVELOPMENT.md) §6.

## Related

- [cmp-supabase-auth-compose](../cmp-supabase-auth-compose/README.md) — Compose UI
- [TARGET_MATRIX.md](../TARGET_MATRIX.md) — measured target policy
- [DEVELOPMENT.md](DEVELOPMENT.md) — contributor docs
