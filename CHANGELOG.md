# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each released version below has a `## [x.y.z]` heading. That heading is not decoration: the
`Release Notes` workflow looks up the section matching the release tag and prepends it to the
GitHub release body, and CI refuses a version bump that arrives without one. Keep the top
section in step with `supabaseauth.version` in `gradle.properties`.

## [Unreleased]

Nothing yet.

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
  `SupabaseAuthUiState`, `SignInLauncher`, and the sign-in path diagnostics
  (`diagnoseSignInPaths`, `SignInPathReport`, `ProviderSignInPath`, `SignInPath`).
- `diagnoseSignInPaths(config)` reports whether each provider resolves to the **native** sheet or
  the **web/browser** fallback on the current platform, without starting a flow. A blank
  `googleWebClientId` makes ComposeAuth route Google through the external browser silently —
  sign-in still works, so nothing surfaces it. Log `SignInPathReport.format()` at startup, or
  assert on `webFallbacks` in a test, to catch that before release rather than at App Review.
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
- Sign-in paths, verified against supabase-kt 3.8.0 rather than assumed: **Google** is native on
  Android (Credential Manager) and on iOS (GoogleSignIn SDK), in both cases only when
  `googleWebClientId` is set — otherwise it falls back to the browser. **Apple** is native on iOS
  (`ASAuthorizationController`) and browser-based everywhere else, since Apple ships no native SDK
  off-iOS. The browser fallback on iOS is the **external Safari app**
  (`UIApplication.sharedApplication.openURL`), not `SFSafariViewController` and not
  `ASWebAuthenticationSession`; no Apple-target artifact links `SafariServices` or `WebKit`.
- Native Google on iOS additionally requires the consuming iOS app to add `GoogleSignIn-iOS` 9.0.0
  via SPM. That is an Xcode-project dependency this library cannot supply.

[Unreleased]: https://github.com/MobileByteLabs/kmp-supabase-auth/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/MobileByteLabs/kmp-supabase-auth/releases/tag/v0.1.0
