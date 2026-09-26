# auth-core

> **Target support:** see [TARGET_MATRIX.md](../TARGET_MATRIX.md) — the single source of truth for
> which KMP targets every module ships and why.

Headless half of **KMP Supabase Auth**. Configuration, the Supabase client boundary, a
Store5-backed session store, the repository, and the Koin wiring. No Compose — the UI lives in
[auth-compose](../auth-compose/README.md).

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Install

```kotlin
dependencies {
    implementation("io.github.mobilebytelabs:auth-core:0.1.0")
}
```

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

Backed by Store5, and deliberately **memory-only** with `Fetcher.ofFlow`:

- Not disk-cached — GoTrue already persists and refreshes the session. Re-caching it here would
  let the app show a signed-in user after the real token had expired.
- Not enrolled in the logout purge — this store is what *tells* the app a logout happened.
  Purging it would clear the very stream the app reads to notice the purge.

## Status

Pre-1.0 and incomplete: `SupabaseAuthConfig` and `SupabaseAuth` have landed; the client, session
store, repository and DI modules described above are the committed design, not yet the shipped
code. See [DEVELOPMENT.md](DEVELOPMENT.md) §6.

## Related

- [auth-compose](../auth-compose/README.md) — Compose UI
- [TARGET_MATRIX.md](../TARGET_MATRIX.md) — measured target policy
- [DEVELOPMENT.md](DEVELOPMENT.md) — contributor docs
