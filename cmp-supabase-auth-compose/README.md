# cmp-supabase-auth-compose

> **Target support:** see [TARGET_MATRIX.md](../TARGET_MATRIX.md) — the single source of truth for
> which KMP targets every module ships and why.

Compose Multiplatform UI for **KMP Supabase Auth** — native sign-in wrappers, brand-compliant
provider buttons, a slot-based login screen and a session-driven ViewModel. Depends on
[cmp-supabase-auth](../cmp-supabase-auth/README.md), which owns everything headless.

<!-- docs-gen:badges:begin -->
[![Version](https://img.shields.io/badge/version-0.1.0-3ecf8e.svg)](https://github.com/MobileByteLabs/kmp-supabase-auth/blob/dev/gradle.properties)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.mobilebytelabs/cmp-supabase-auth-compose?label=maven%20central)](https://central.sonatype.com/artifact/io.github.mobilebytelabs/cmp-supabase-auth-compose)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.12.0-blue.svg)](https://www.jetbrains.com/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](../LICENSE)
<!-- docs-gen:badges:end -->

## Install

<!-- docs-gen:install:begin -->
```kotlin
dependencies {
    implementation("io.github.mobilebytelabs:cmp-supabase-auth-compose:0.1.0")
}
```
<!-- docs-gen:install:end -->

<!-- docs-gen:targets:begin -->
This module ships **6 targets**:

- `android`
- `iosArm64`
- `iosSimulatorArm64`
- `js`
- `jvm`
- `wasmJs`
<!-- docs-gen:targets:end -->

`cmp-supabase-auth` comes transitively — it is an `api` dependency, because the login screen takes
`AuthRepository` and `SupabaseAuthClient` from it.

## Koin module

```kotlin
val AppModule = module {
    includes(
        supabaseAuth(config),        // cmp-supabase-auth: client + store + repository
        supabaseAuthComposeModule(), // this module: the ViewModel
    )
}
```

`supabaseAuthComposeModule()` binds `SupabaseAuthViewModel` as a **factory**, not a single: each
login screen gets its own, so a sign-in attempt abandoned on one screen cannot leave stale
loading or error state visible on the next.

## Usage

```kotlin
SupabaseLoginScreen(
    viewModel = koinViewModel(),
    client = koinInject(),
    header = { YourLogo() },      // slots for branding — no need to fork the screen
    footer = { TermsAndPrivacy() },
    showGuestOption = true,
    onSignedIn = { navigateHome() },
)
```

Or compose your own from the parts:

```kotlin
val google = rememberGoogleSignIn(client, onError = viewModel::onSignInFailed)
GoogleSignInButton(onClick = { viewModel.onSignInStarted(); google.launch() })
AppleSignInButton(onClick = { /* … */ })
ContinueAsGuestButton(onClick = viewModel::continueAsGuest)
```

## Success comes from the session, not the button callback

On Android the native Google `onResult(Success)` callback **frequently never fires** even though
the ID-token exchange succeeded and the session landed. This was verified on-device: the app sat
on "Signing in…" while Supabase logged `Authenticated`.

So the ViewModel derives signed-in from `AuthRepository.isSignedIn`, and `onResult` is used only
for `Error` / `NetworkError` / `ClosedByUser`, which do fire reliably. If you build your own
screen, do the same — a callback-driven UI hangs on a spinner while the user is already signed in.

`ClosedByUser` is reported as `AuthError.Cancelled` and deliberately does **not** surface as an
error: a user dismissing the provider sheet has not hit a failure.

## Provider buttons

`GoogleSignInButton` and `AppleSignInButton` render `compose-auth-ui`'s `ProviderButtonContent`
rather than hand-drawn icons. Apple and Google both publish brand requirements — Apple mandates
the exact phrase "Sign in with Apple" — and a bespoke mark risks store review.

## macOS has no native Apple sign-in

`compose-auth` publishes **no macOS artifact at all**, so this module cannot reach macOS. macOS
consumers take `cmp-supabase-auth` plus the web-OAuth fallback. See
[docs/SETUP_APPLE.md](../docs/SETUP_APPLE.md).

`cmp-supabase-auth` reaches `iosX64` and this module does not — Compose Multiplatform publishes no
`iosX64` artifact. Both losses are measured; see [TARGET_MATRIX.md](../TARGET_MATRIX.md).

## Status

Pre-1.0 and implemented: the API below is shipped, tested and green on every declared target.
Not yet published to Maven Central, and native sign-in is unverified on a physical device.
See [DEVELOPMENT.md](DEVELOPMENT.md) §6.

## Related

- [cmp-supabase-auth](../cmp-supabase-auth/README.md) — headless half
- [TARGET_MATRIX.md](../TARGET_MATRIX.md) — measured target policy
- [DEVELOPMENT.md](DEVELOPMENT.md) — contributor docs
