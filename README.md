# KMP Supabase Auth

**Supabase authentication for Kotlin Multiplatform** — native Google sign-in through the Android
Credential Manager, native Apple sign-in through `ASAuthorization` on iOS, anonymous sessions with
an id-preserving upgrade, and a web-OAuth fallback everywhere else.

Wire it into an app with **one Koin line**. Or three, one per architectural layer — they are the
same thing, and a test proves it.

<!-- docs-gen:badges:begin -->
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/cmp-supabase-auth?label=maven%20central)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/cmp-supabase-auth)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.12.0-blue.svg)](https://www.jetbrains.com/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
<!-- docs-gen:badges:end -->

> **Pre-1.0.** The API is implemented, tested and green across every declared target, published to
> Maven Central, and sign-in is verified on physical devices — Android and iOS — by a consuming app.
> What that verification changed is worth reading before you wire it up:
> [Proven on a device](#proven-on-a-device). See also [Status](#status).

---

## Why this exists

Four apps in this workspace had each built their own Supabase sign-in. Only one of them actually
worked. The other three were a generic provider interface wired to nothing, a REST client with the
native token hardcoded to `""`, and a Supabase client with no auth at all.

Auth is the worst thing to re-implement per app: a bug in it is a security bug, and a fix that only
reaches the *next* project is close to worthless. So this is one library, versioned independently,
that every app can depend on.

## Install

<!-- docs-gen:install:begin -->
Set `supabaseAuthVersion` to the version shown by the **Maven Central** badge above —
that badge is read live from the registry and is always the latest published release.

```kotlin
repositories {
    mavenCentral()
    google() // see the note below — required only for cmp-supabase-auth-compose
}

val supabaseAuthVersion = "<see the Maven Central badge>"

dependencies {
    implementation("io.github.mobilebytelabs:cmp-supabase-auth:$supabaseAuthVersion")  // headless
    implementation("io.github.mobilebytelabs:cmp-supabase-auth-compose:$supabaseAuthVersion")  // Compose UI (optional)
}
```

> **Why `google()`?** `cmp-supabase-auth-compose` pulls in Compose Multiplatform, whose
> transitive AndroidX dependencies (`androidx.savedstate`, `androidx.lifecycle-*`) are
> published to Google's Maven repository and are **not** mirrored to Maven Central.
> Verified by resolving both modules against Central alone: the build fails on those
> transitive artifacts, not on this library's own.
>
> **`cmp-supabase-auth` alone needs only `mavenCentral()`.** If you are not using the
> Compose bindings, you do not need `google()` for this library at all.
<!-- docs-gen:install:end -->

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

<!-- docs-gen:modules:begin -->
| Module | Artifact | Targets | Latest |
|---|---|---|---|
| [`cmp-supabase-auth`](cmp-supabase-auth/README.md) | `io.github.mobilebytelabs:cmp-supabase-auth` | 17 | [![](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/cmp-supabase-auth?label=%20)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/cmp-supabase-auth) |
| [`cmp-supabase-auth-compose`](cmp-supabase-auth-compose/README.md) | `io.github.mobilebytelabs:cmp-supabase-auth-compose` | 6 | [![](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/cmp-supabase-auth-compose?label=%20)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/cmp-supabase-auth-compose) |
| `sample-app` | — (not published) | — | — |
<!-- docs-gen:modules:end -->

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

## Proven on a device

A consuming app (`mbs/cappy`) integrated this library and ran it on an iPhone 13 and an Android 15
device. Four things that passed every unit test and every CI gate were wrong, and each is now fixed.
They are recorded here because all four are invisible from the API surface.

### 1. Native iOS Google cannot work from a published library

supabase-kt drives native iOS Google through a cinterop bridge
(`compose-auth-iosArm64Cinterop-GoogleSignInNativeBridge`) that compiles against headers and **links
nothing** — its klib's `libraryPaths`/`linkerOpts` point at the supabase-kt CI build directory and it
embeds no `.a` and no `.framework`. The Kotlin side builds, the app links, and the **first tap
aborts**.

Supplying the SDK requires an SPM declaration in a package Xcode resolves, and Xcode resolves that
graph *before* any Gradle task runs. So the dependency cannot arrive from Maven, and the earlier
advice to "add GoogleSignIn-iOS via SPM" pushes the work onto every consumer with nothing able to
check it.

`KmpSupabaseAuthClient.supportsNativeGoogle` now reports `false` on iOS and the launchers route to an
in-app `ASWebAuthenticationSession`, which authenticates against the **web** client.
`KmpSupabaseAuthConfig.googleIosClientId` was removed in `0.3.0`: it implied "configure this and
native iOS works", which was never true.

### 2. The session arrives in the URL *fragment*, not as a PKCE code

supabase-kt defaults to `FlowType.IMPLICIT` (read out of `AuthConfigDefaults` in 3.8.0), so the
callback carries tokens in the fragment rather than `?code=`. The iOS handler searched only the
query, found nothing, and reported `Cancelled` — discarding a valid session and making a *successful*
sign-in look like the person had dismissed the sheet. The device trace:

```
GOOGLE: callback received — query=[] fragment=[access_token, expires_at, expires_in,
                                              provider_token, refresh_token, sb, token_type]
```

Both shapes are handled now, rather than pinning a flow type — the flow is the consumer's choice.
`importAuthToken` is called with `retrieveUser = true`, because the implicit callback returns tokens
only and the session would otherwise be authenticated with a null user, so profile mapping reads
blanks after a successful sign-in.

### 3. A cancelled provider reports nothing at all

Dismissing the sheet produced no success, no error, and no cancellation. The only thing that ended
the flow was the 60-second no-response watchdog, which then reported the wrong class with a message
about signing certificates:

```
12:18:12.8  Custom Tab window hidden            (dismissed)
12:18:13.0  CustomTabActivity DESTROYED
12:20:35.4  GOOGLE: TIMEOUT after 1m — the provider never called back
```

Two minutes and twenty-two seconds of spinner, with every button disabled, for someone who changed
their mind. The launcher now races a **return-to-foreground** signal against the timeout — the one
thing a provider cannot withhold, since the sheet is gone and the app is interactive again. A 1.5s
grace follows it, because a genuine callback often lands just *after* the host Activity resumes and
reporting instantly would turn a successful sign-in into a spurious cancellation.

Re-measured after the fix, both paths covered by different mechanisms:

```
GOOGLE: onResult = ClosedByUser (dismissed)                              ← 1.66s, native path
APPLE:  app returned to the foreground with no result after 1.5s         ← the new detector
        — treating as cancelled by the user
```

### 4. Diagnostics defaulted to silence, which hid all of the above

`KmpSupabaseAuthLog` required an explicit `handler`. No consumer ever set one, so a failing iOS
sign-in produced a device log with **zero** lines about the sign-in path — an uninstalled handler and
a flow that was never reached look identical. Debug builds now log by default
(`Platform.isDebugBinary` on native, the host app's `FLAG_DEBUGGABLE` on Android), still never
printing credentials. With no consumer wiring at all:

```
install: GOOGLE native (serverClientId=set(72 chars))          ← Android
install: GOOGLE native SKIPPED — this platform has no native   ← iOS
         Google path … the client id is NOT the problem
```

Same binary, opposite answers — the platform split behaving as designed rather than as asserted in a
test.

### Also worth knowing

- **The library ships no consumer ProGuard rules.** Verified by unzipping the published AAR: zero
  proguard entries. A minifying consumer needs keep rules for
  `io.github.mobilebytelabs.supabaseauth.**` — Koin resolves the client reflectively and the session
  models are `@Serializable`, both invisible to R8.
- **Declare `AuthConfig` above your Koin module.** Kotlin initialises top-level properties in
  declaration order, and `includes(kmpSupabaseAuthNetwork(config = AuthConfig))` reads it while the
  module object is being constructed. Below it, the config is uninitialised — compiles cleanly, fails
  at runtime.
- **Success is the session stream, never the callback.** On Android the native `Success` callback
  frequently never fires even though the exchange succeeded, so a screen waiting on it hangs while
  the person is already signed in.

## Platform support

|  | Android | iOS | Desktop | Web | macOS |
|---|---|---|---|---|---|
| Google | native (Credential Manager) | **in-app web OAuth** | OAuth redirect | OAuth redirect | OAuth redirect |
| Apple | **in-app web OAuth** | native (ASAuthorization) | OAuth redirect | OAuth redirect | OAuth redirect |
| Anonymous | ✅ | ✅ | ✅ | ✅ | ✅ |

**iOS Google is web, not native — and that is not a limitation this library can lift.** The native
iOS path needs the GoogleSignIn SDK in the *app's own* Xcode/SPM graph, and Xcode resolves that graph
before any Gradle task runs, so no Maven-published library can put it there. `supportsNativeGoogle`
reports `false` on iOS and the launchers route to an in-app `ASWebAuthenticationSession`. Native
**Apple** works on iOS because `AuthenticationServices` is a *system* framework — that asymmetry is
the whole explanation, and it is why Apple sign-in worked on a real device while Google aborted.

**"In-app web OAuth" means in-app.** Chrome Custom Tabs in the app's own task on Android,
`ASWebAuthenticationSession` on iOS — not the external browser. That distinction is an App Store
requirement, not a nicety: a consuming app was rejected under Guideline 4 - Design for being
"taken to the default web browser to sign in".

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

<!-- docs-gen:deps:begin -->
| Dependency | Version |
|---|---|
| supabase-kt (`auth-kt`, `compose-auth`, `compose-auth-ui`) | `3.8.0` |
| Koin | `4.1.1` |
| Kotlin | `2.4.20` |
| Compose Multiplatform | `1.12.0` |
<!-- docs-gen:deps:end -->

### CI

| Workflow | Runs |
|---|---|
| `pr-check.yml` | Quality + JVM tests, docs gate, Kover coverage, BCV `apiCheck` |
| `gradle.yml` | Full multi-platform build on push |
| `native-tests.yml` | Kotlin/Native test execution, nightly / opt-in |
| `development-md-coherence.yml` | `DEVELOPMENT.md` structure per module |
| `publish.yml` / `publish-trigger.yml` | Maven Central publishing |
| `docs-refresh.yml` | regenerates derived docs on merge, then deploys |
| `docs-publish.yml` / `sync-docs-to-wiki.yml` | docsify site → Cloudflare Pages + wiki |

The quality stack — Kover, Detekt, Spotless, BCV, the docs gate, native tests — is ported from
[KmpToolkit](https://github.com/MobileByteLabs/KmpToolkit). Its observability gate is deliberately
**not** ported: `cmp-observe`'s published jvm artifact is compiled at Java 21, which would force
JDK 21 on every desktop consumer of this library.

## Status

| Area | State |
|---|---|
| Build, CI, publishing, quality gates | ✅ complete |
| Config, client, session store, repository, Koin DI | ✅ implemented, tested |
| Compose UI — buttons, login screen, ViewModel | ✅ implemented, tested |
| Public API entirely commonMain | ✅ enforced by BCV |
| Setup documentation | ✅ complete |
| Published to Maven Central | ✅ `0.3.0` |
| Sign-in verified on physical devices | ✅ Android 15 + iPhone 13 — [what it proved](#proven-on-a-device) |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Each module's `DEVELOPMENT.md`
([cmp-supabase-auth](cmp-supabase-auth/DEVELOPMENT.md) · [cmp-supabase-auth-compose](cmp-supabase-auth-compose/DEVELOPMENT.md)) carries the
per-module contributor docs, and CI enforces their structure.

## License

Apache 2.0 — see [LICENSE](LICENSE).
