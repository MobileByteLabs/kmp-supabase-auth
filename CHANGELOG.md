# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each released version below has a `## [x.y.z]` heading. That heading is not decoration: the
`Release Notes` workflow looks up the section matching the release tag and prepends it to the
GitHub release body, and CI refuses a version bump that arrives without one. Keep the top
section in step with `supabaseauth.version` in `gradle.properties`.

## [Unreleased]

### Changed

- AGP `9.4.1` → `9.4.0`, matching `cappy` and `kmp-toolkit`. The library was the only repo in the
  org on 9.4.1, and a composite build runs both builds on the root's Gradle — a mismatched AGP
  pair yields wrong KMP metadata that surfaces as `Unresolved reference` in `commonMain`, far from
  its cause. Aligning here rather than bumping consumers keeps the blast radius to this repo.

## [0.1.3] - 2026-09-27

### Added

- `AuthSession` — `user` + `isSignedIn` as one consistent value, exposed as
  `AuthRepository.session: StateFlow<AuthSession>`. Reading `currentUser` and `isSignedIn`
  separately lets a collector observe them a frame apart in a combination that never occurred;
  `session` is assigned at the same instant as both, inside the store's single update point.
  It distinguishes three states rather than two — `isSignedOut`, `isGuest` (a real, upgradeable
  anonymous session) and `isAuthenticated` — because a two-boolean shape cannot tell an anonymous
  session from a real account, and anonymous is the state most likely to need different UI.
  Carries identity only: entitlements and profile rows stay with the app, which already owns them.
- `SupabaseAuthClient.composeAuth` in `cmp-supabase-auth-compose` — the underlying `ComposeAuth`
  plugin, for flows the `remember*` wrappers do not cover. Deliberately an extension in the Compose
  module: declaring it on the headless interface would pull in `compose-auth` (7 targets) and cut
  the headless module from 17 targets to 7 for consumers who use no Compose at all.
- Install docs now state the repository requirement. `cmp-supabase-auth-compose` needs `google()`
  alongside `mavenCentral()`, because Compose Multiplatform's transitive AndroidX dependencies
  (`androidx.savedstate`, `androidx.lifecycle-*`) are published only to Google's Maven repository.
  `cmp-supabase-auth` on its own resolves from `mavenCentral()` alone. Established by resolving
  both modules from Central in a clean consumer build, not by assumption.

### Fixed

- Per-module install snippets wrapped the dependency in no `dependencies { }` block, so the
  copy-pasted Kotlin was invalid.

- `release-notes.yml` no longer fires on `release: edited`. Enrichment prepends the changelog
  section above the current body, so firing on every edit meant any manual curation of a release
  was re-prepended over within seconds — the workflow fought the human. It now runs on `created`
  / `published`, with `workflow_dispatch` for a deliberate re-enrich.

## [0.1.2] - 2026-09-27

**The first version published to Maven Central.** `0.1.0` and `0.1.1` were both tagged but never
produced an artifact — each publish failed during Gradle configuration, before any upload — so
nothing was ever available at either. This release carries the full library described under
`[0.1.0]` plus the fixes below.

### Fixed

- Publishing now matches `kmp-toolkit` exactly: the modules call neither `coordinates(...)` nor
  `publishToMavenCentral()`, and `SONATYPE_HOST` / `SONATYPE_AUTOMATIC_RELEASE` are committed to
  `gradle.properties`. `coordinates(...)` set the plugin's `version` after the publish workflow's
  injected `VERSION_NAME` had finalized it, failing both the 0.1.0 and 0.1.1 publishes.
- `androidLibrary { }` → `android { }`, matching kmp-toolkit and clearing the AGP deprecation.
- Documentation no longer states the library version as a literal anywhere. Install snippets and
  the module table point at the live Maven Central badge, which is read from the registry and
  cannot go stale; a CI gate rejects any reintroduced literal.

## [0.1.1] - 2026-09-27

> **Never published to Maven Central.** Like `0.1.0`, this tag exists on GitHub but produced no
> artifact — the publish failed during Gradle configuration, before any upload. The fixes below
> addressed the wrong cause: the real one was `coordinates(...)` setting the plugin's version
> after `VERSION_NAME` had finalized it, which is fixed in `[0.1.2]`. Use **`0.1.2` or later**.

These changes are included in `0.1.2`.

### Fixed

- The Maven Central publish failed at configuration with "The value for this property is final
  and cannot be changed any further". The publish workflow appends `SONATYPE_HOST=CENTRAL_PORTAL`
  to `gradle.properties`, and the build file also called `publishToMavenCentral()`, so the host
  was configured twice. The call is now skipped when that property is present, and kept for
  manual publishing. `./gradlew build` never configures the publish task, which is why every
  local and PR check was green while the release could not publish — a CI dry-run of the publish
  task now runs on every PR.
- `release-notes.yml` passed `fail-on-missing`, but the pinned reusable workflow declares
  `fail-when-missing`. An unknown input fails a reusable workflow at startup, so the v0.1.0
  release was created with no changelog attached.

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

[Unreleased]: https://github.com/MobileByteLabs/kmp-supabase-auth/compare/v0.1.3...HEAD
[0.1.3]: https://github.com/MobileByteLabs/kmp-supabase-auth/releases/tag/v0.1.3
[0.1.2]: https://github.com/MobileByteLabs/kmp-supabase-auth/releases/tag/v0.1.2
[0.1.1]: https://github.com/MobileByteLabs/kmp-supabase-auth/releases/tag/v0.1.1
[0.1.0]: https://github.com/MobileByteLabs/kmp-supabase-auth/releases/tag/v0.1.0
