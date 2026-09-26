# KMP Supabase Auth

**Supabase authentication for Kotlin Multiplatform** — native Google sign-in through the Android
Credential Manager, native Apple sign-in through `ASAuthorization` on iOS, anonymous sessions with
an id-preserving upgrade, and a web-OAuth fallback everywhere else.

Wire it into an app with **one Koin line**. Or three, one per architectural layer — they are the
same thing, and a test proves it.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.12.0-blue.svg)](https://www.jetbrains.com/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

> **Pre-1.0 and under construction.** The build, CI, publishing and quality stack are complete and
> green. The auth implementation is partially landed — see [Status](#status). The API below is the
> committed design; treat it as a contract, not as shipped code.

---

## Why this exists

Four apps in this workspace had each built their own Supabase sign-in. Only one of them actually
worked. The other three were a generic provider interface wired to nothing, a REST client with the
native token hardcoded to `""`, and a Supabase client with no auth at all.

Auth is the worst thing to re-implement per app: a bug in it is a security bug, and a fix that only
reaches the *next* project is close to worthless. So this is one library, versioned independently,
that every app can depend on.

## Install

```kotlin
dependencies {
    implementation("io.github.mobilebytelabs:cmp-supabase-auth:0.1.0")     // headless
    implementation("io.github.mobilebytelabs:cmp-supabase-auth-compose:0.1.0")  // Compose UI (optional)
}
```

## Quick start

```kotlin
val AppModule = module {
    includes(
        supabaseAuth(
            SupabaseAuthConfig(
                projectRef        = "your-project-ref",
                googleWebClientId = BuildKonfig.GOOGLE_OAUTH_WEB_CLIENT_ID,
                redirectUrl       = "myapp://login-callback",
            ),
        ),
        supabaseAuthComposeModule(),
    )
}
```

```kotlin
SupabaseLoginScreen(
    viewModel = koinViewModel(),
    client = koinInject(),
    header = { YourLogo() },
    onSignedIn = { navigateHome() },
)
```

Console setup is the part that actually costs time:
**[Google](docs/SETUP_GOOGLE.md)** · **[Apple](docs/SETUP_APPLE.md)**.

## Modules

| Module | Targets | What it owns |
|---|---|---|
| **[cmp-supabase-auth](cmp-supabase-auth/README.md)** | 8 | Config, Supabase client boundary, Store5 session store, repository, Koin DI |
| **[cmp-supabase-auth-compose](cmp-supabase-auth-compose/README.md)** | 6 | `remember*` sign-in wrappers, provider buttons, login screen, ViewModel |
| `sample-app` | — | Runnable proof. Not published |

Target counts are **measured** against Maven Central, not inferred — see
**[TARGET_MATRIX.md](TARGET_MATRIX.md)**.

## Two wiring modes, one implementation

Drop it in anywhere:

```kotlin
includes(supabaseAuth(config))
```

…or place each rung in the layer it belongs to:

```kotlin
// core/network/di/ProjectNetworkModule.kt   ← owner:fork, survives template sync
includes(supabaseAuthNetwork(config))

// core/store/di/StoreModule.kt
includes(supabaseAuthStore())

// core/data/di/ProjectRepositoryModule.kt
includes(supabaseAuthRepository())
```

`supabaseAuth(config)` is *defined as* those three includes, so the two forms cannot drift apart.
A test asserts their Koin binding sets are identical — the guarantee is enforced, not documented.

## Three things worth knowing before you build on it

**It never creates a Supabase client.** The library installs `Auth` and `ComposeAuth` onto the one
your app already has, through kmp-project-template's `SupabaseExtrasProvider` seam. Building a
second client to get `Auth` hands your generated API bindings a different instance carrying no
session — so every RLS-gated call resolves no `auth.uid()`, while compiling cleanly and passing
static checks the whole way.

**Signed-in comes from the session stream, never the button callback.** On Android the native
Google `onResult(Success)` callback frequently never fires even though the exchange succeeded and
the session landed — verified on-device, with the app stuck on "Signing in…" while Supabase logged
`Authenticated`. The ViewModel watches `AuthRepository.isSignedIn`; `onResult` is used only for
error cases, which do fire reliably.

**The library owns the session; your app keeps owning the user.** Profile data stays in your
`UserDataStore` / `UserPreferencesRepository`, untouched. `AuthUser` carries identity only. There
is never a second owner of state you already own.

## Guest sessions

`signInAnonymously()` creates a real Supabase session with `is_anonymous = true`, so RLS works
immediately and guest data lives server-side from the first write. `linkIdentity(provider)`
upgrades a guest **without changing the user id**, so nothing has to be migrated.

The limit, stated rather than papered over: upgrade works on the same install. A guest who signs
in on a second device gets a different id. Cross-device guest merge is out of scope.

## Platform support

|  | Android | iOS | Desktop | Web | macOS |
|---|---|---|---|---|---|
| Google | native (Credential Manager) | native | OAuth redirect | OAuth redirect | OAuth redirect |
| Apple | OAuth redirect | native (ASAuthorization) | OAuth redirect | OAuth redirect | OAuth redirect |
| Anonymous | ✅ | ✅ | ✅ | ✅ | ✅ |

**macOS gets no native Apple sign-in** — `compose-auth` publishes no macOS artifact at all, so
`cmp-supabase-auth-compose` cannot reach macOS. Measured, not an oversight.

## Development

```bash
./gradlew build                 # all modules, all targets
./gradlew jvmTest               # fast loop
./gradlew koverHtmlReport       # coverage
./gradlew apiDump               # regenerate BCV baselines after an API change
./gradlew spotlessApply detekt  # format + static analysis
./ci-prepush.sh                 # what CI runs, locally
```

Requires JDK 17+. CI runs 21.

### CI

| Workflow | Runs |
|---|---|
| `pr-check.yml` | Quality + JVM tests, docs gate, Kover coverage, BCV `apiCheck` |
| `gradle.yml` | Full multi-platform build on push |
| `native-tests.yml` | Kotlin/Native test execution, nightly / opt-in |
| `development-md-coherence.yml` | `DEVELOPMENT.md` structure per module |
| `publish.yml` / `publish-trigger.yml` | Maven Central publishing |
| `docs-publish.yml` / `sync-docs-to-wiki.yml` | mkdocs site + wiki |

The quality stack — Kover, Detekt, Spotless, BCV, the docs gate, native tests — is ported from
[KmpToolkit](https://github.com/MobileByteLabs/KmpToolkit). Its observability gate is deliberately
**not** ported: `cmp-observe`'s published jvm artifact is compiled at Java 21, which would force
JDK 21 on every desktop consumer of this library.

## Status

| Area | State |
|---|---|
| Build, CI, publishing, quality gates | ✅ complete |
| `SupabaseAuthConfig`, `SupabaseAuth.validate` | ✅ landed, tested |
| Client, session store, repository, Koin modules | 🚧 designed, not yet implemented |
| Compose UI — buttons, login screen, ViewModel | 🚧 designed, not yet implemented |
| Setup documentation | ✅ complete |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Each module's `DEVELOPMENT.md`
([cmp-supabase-auth](cmp-supabase-auth/DEVELOPMENT.md) · [cmp-supabase-auth-compose](cmp-supabase-auth-compose/DEVELOPMENT.md)) carries the
per-module contributor docs, and CI enforces their structure.

## License

Apache 2.0 — see [LICENSE](LICENSE).
