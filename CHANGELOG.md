# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each released version below has a `## [x.y.z]` heading. That heading is not decoration: the
`Release Notes` workflow looks up the section matching the release tag and prepends it to the
GitHub release body, and CI refuses a version bump that arrives without one. Keep the top
section in step with `supabaseauth.version` in `gradle.properties`.

## [Unreleased]

### Added

- `diagnoseSignInPaths(config)` in `cmp-supabase-auth-compose` — reports whether each provider
  will take the **native** sheet or the **web/browser** fallback on the current platform, without
  starting a flow. Returns `SignInPathReport` with a per-provider reason, a `webFallbacks` list to
  assert on in tests, and `format()` for a startup log line.

  This closes a silent-degradation hole: a blank `googleWebClientId` makes
  `supabaseComposeAuthExtras` skip `googleNativeLogin(...)`, ComposeAuth then sees a null
  `googleLoginConfig` and quietly routes Google through the external Safari app. Sign-in still
  works, so nothing surfaces the change — an app can reach App Review showing a web login it
  never meant to ship. The report makes that visible before release.

### Fixed

- Corrected the documented iOS OAuth behaviour. The KDoc claimed iOS returned through
  `ASWebAuthenticationSession`; it does not. supabase-kt 3.8.0 launches the redirect with
  `UIApplication.sharedApplication.openURL(...)`, which opens the **external Safari app** —
  verified against upstream `Auth/src/iosMain/.../openUrl.kt` and against the shipped iOS klib,
  which declares neither `SafariServices` nor `WebKit`. No in-app browser is used on iOS, and
  `SFSafariViewController` is not supported. Behaviour is unchanged; only the docs were wrong.

## [0.1.0] - 2026-09-27

First release. Supabase authentication for Kotlin Multiplatform, published as two independently
consumable modules.

### Added

- `cmp-supabase-auth` (17 targets) — the headless core, usable without Compose.
  Public surface: `SupabaseAuth`, `SupabaseAuthClient`, `SupabaseAuthConfig`,
  `SupabaseAuthOptions`, `AuthRepository`, `AuthSessionStore`, `AuthUser`, `AuthProvider`,
  `AuthError`, and `FakeAuthRepository` for consumer tests.
- `cmp-supabase-auth-compose` (6 targets) — Compose Multiplatform bindings built on
  supabase-kt's ComposeAuth and ComposeAuthUI. Public surface: `SupabaseAuthViewModel`,
  `SupabaseAuthUiState`, `SignInLauncher`.
- Native Google sign-in through the Android Credential Manager.
- Native Apple sign-in through `ASAuthorization` on iOS.
- Anonymous/guest sessions with an id-preserving upgrade to a full account.
- Web-OAuth fallback on every target without a native provider.
- Koin DI wiring consumable from an app's `ProjectNetworkModule.kt`, as a single line or as
  three per-rung lines across `core/network`, `core/store` and `core/data`.

### Notes

- The library never calls `createSupabaseClient`. It installs `Auth` and `ComposeAuth` into the
  consumer's existing client through the `SupabaseExtrasProvider` seam. A second client carries
  no session, so every RLS-gated call would resolve no `auth.uid()` while still compiling.
- `sessionStatus` is the success signal, not the provider callback. On Android the native Google
  `onResult(Success)` callback frequently never fires even though the exchange succeeded, so
  `onResult` is used only for Error, NetworkError and ClosedByUser.
- Built against supabase-kt 3.8.0 and Kotlin 2.4.20. ComposeAuthUI is experimental upstream; the
  opt-in is contained to the one file that needs it rather than leaking to consumers.

[Unreleased]: https://github.com/MobileByteLabs/kmp-supabase-auth/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/MobileByteLabs/kmp-supabase-auth/releases/tag/v0.1.0
