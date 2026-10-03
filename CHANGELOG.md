# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Each released version below has a `## [x.y.z]` heading. That heading is not decoration: the
`Release Notes` workflow looks up the section matching the release tag and prepends it to the
GitHub release body, and CI refuses a version bump that arrives without one. Keep the top
section in step with `supabaseauth.version` in `gradle.properties`.

## [Unreleased]

### Fixed — a publish now completes the release cycle

- **`bump-after-release: true`.** The reusable workflow's `Open Bump PR (next cycle)` job was wired
  correctly and simply switched off: it reported `skipped` on the successful v0.3.0 publish
  (run 37114009332), which from the outside reads exactly like "not implemented".

  It matters because `gradle-properties-key` makes `supabaseauth.version` the version source of
  truth and the changelog gate refuses a release whose `## [x.y.z]` section is missing. Left at the
  just-released number, the next publish either re-cuts a duplicate — Maven Central rejects it,
  observed on run 37115504768: *"Component with package url … already exists"* — or depends on the
  bump being remembered by hand at the moment attention is lowest.

- `assert-publish-tags.sh` gains **PT-5**, asserting `bump-after-release: true`, alongside PT-1..PT-4
  which already guard that every publish path creates its tag and release. Verified against both
  regression shapes: the flag set back to `false`, and the input deleted outright. The two halves are
  one invariant — a publish must leave neither the tag nor the version behind.

  v0.3.0 is the first release where a `workflow_dispatch` publish produced its own tag
  (`v0.3.0` → `5507b900`, 2026-10-03T09:51Z). v0.2.0 reached Central with none.

## [0.3.0] - 2026-10-03

### Fixed — cancelling a provider now reports Cancelled, in about a second

- **Dismissing the Google or Apple sheet left the screen on "Signing in…" with every button
  disabled.** A cancelled provider reports NOTHING — no success, no error, no cancellation — so the
  only thing that ever ended the flow was the 60-second no-response watchdog, which then reported
  `NoResponse` (the wrong class) with a message blaming the OAuth client's signing certificate.

  MEASURED on `mbs/cappy`, Android 15, 2026-10-03:

  ```
  12:18:12.8  Custom Tab window hidden            (dismissed)
  12:18:13.0  CustomTabActivity DESTROYED, removed from the app's task
  12:20:35.4  [supabase-auth] GOOGLE: TIMEOUT after 1m — the provider never called back
  ```

  Two minutes and twenty-two seconds of spinner for someone who changed their mind, ending in an
  error about certificates.

  The launcher now RACES two signals: the app returning to the foreground with no result →
  `Cancelled`, against the existing timeout → `NoResponse`. Returning to the foreground is the one
  thing a provider cannot withhold — the sheet is gone and the app is interactive again.

  A 1.5s grace period follows the foreground signal before anything is reported, because a genuine
  callback often lands a beat AFTER the host Activity resumes; reporting instantly would turn a
  SUCCESSFUL sign-in into a spurious "cancelled", which is worse than the bug. A result arriving
  during the grace cancels the whole job, so nothing is reported.

  New `awaitAppForegroundReturn()` — real on Android (`ActivityLifecycleCallbacks` on the
  Application the library already captures) and iOS (`UIApplicationDidBecomeActive`); a
  never-emitting no-op on desktop/JS/Wasm and the non-iOS Apple leaves, where guessing at the
  transition could report "cancelled" for a sign-in still in progress. The watchdog is KEPT, not
  replaced: Credential Manager renders over the app and may never pause it, in which case the
  timeout is still the only net. Public only because `internal` is module-scoped and the launchers
  live in `cmp-supabase-auth-compose`.

  The timeout message no longer asserts a single cause as "most often".

### Fixed — release plumbing

- **Every version that reaches Maven Central now gets a git tag and a GitHub release.** The
  `create-github-release` input read `${{ github.event_name == 'workflow_dispatch' }}` — an
  ALLOWLIST that was correct for the two triggers that existed and silently wrong for any third. A
  `schedule`, a `repository_dispatch`, a `workflow_call`, or re-enabling the push path would each
  evaluate false and publish to **immutable** Central with no tag, no release and no notes. It now
  reads `${{ github.event_name != 'release' }}` — a denylist of the single event for which creating
  a release is genuinely wrong, because it is what started the run. An allowlist must be edited in
  lockstep with every trigger change; a denylist of the one incompatible event does not.

  Locked by `.github/scripts/assert-publish-tags.sh`, wired into the Docs job: PT-1 the input
  exists · PT-2 it is not a literal `false` · PT-3 it is not an event allowlist · PT-4 it is the
  fail-safe form. Verified against all three regression shapes, the previous
  `== 'workflow_dispatch'` among them.

  `publish-trigger.yml`'s comment claimed publish.yml "sets `create-github-release: false`" and
  gave that as the reason its push trigger stays disabled. That reason no longer holds — the push
  path would now be tagged like any other. It stays disabled on POLICY grounds instead: it would
  publish on every merge to dev touching `cmp-*/**`, making release cadence a side effect of
  merging.

### Added

- **OAuth sign-in now happens inside the app on both mobile platforms.** Apple rejected a consumer
  under App Store **Guideline 4 - Design** — *"the user is taken to the default web browser to sign in
  or register… You may also choose to implement the Safari View Controller API"* — and supabase-kt
  cannot satisfy that on iOS: its `auth-kt`, `compose-auth` and `compose-auth-ui` iOS artifacts contain
  zero references to `SFSafariViewController`, `ASWebAuthenticationSession` or `SafariServices`, and the
  iOS path calls `UIApplication.openURL`, which is the external browser by definition. iOS now presents
  `ASWebAuthenticationSession`; Android launches its Custom Tab from the **current Activity** instead of
  the application context, so the tab stays in the app's task rather than resurfacing as a separate
  browser task. Nothing is required of the consumer — an Android `ContentProvider` captures the
  Application at process start.

- `KmpSupabaseAuthClient.supportsNativeGoogle` — whether this platform can run native Google sign-in
  using only what the library ships. False on iOS, true on Android. A *delivery* fact, independent of
  whether a client id is configured.

- **Diagnostics are on by default in debug builds.** `KmpSupabaseAuthLog` previously defaulted to
  silence on every platform and required an explicit `handler`. No consumer ever set one, so a failing
  iOS sign-in produced a device log with zero lines about the sign-in path. The default is now
  `println` in a debug build of the host app and silence in a release build — detected per platform
  (`Platform.isDebugBinary` on native, the host app's `FLAG_DEBUGGABLE` on Android, false for
  JVM/JS/Wasm). Set `handler` to route elsewhere; set `silenced = true` to quiet a debug build. The
  library's contract is unchanged: it logs decisions and outcomes, never credentials.

### Changed

- **BREAKING — `KmpSupabaseAuthConfig.googleIosClientId` is removed, and iOS no longer attempts native
  Google sign-in.** supabase-kt drives native iOS Google through a cinterop bridge
  (`compose-auth-iosArm64Cinterop-GoogleSignInNativeBridge`) that compiles against headers and **links
  nothing** — its klib's `libraryPaths`/`linkerOpts` point at the supabase-kt CI build directory and it
  embeds no `.a` and no `.framework`. Linking the GoogleSignIn SDK requires an SPM declaration in a
  package **Xcode resolves before any Gradle task runs**, so no Maven-published library can deliver it:
  the Kotlin side builds, the app links, and the first tap aborts at runtime. Keeping an iOS client id
  field implied "configure this and native iOS works", which was never true.

  iOS Google sign-in now uses the in-app `ASWebAuthenticationSession` flow, which authenticates against
  the **web** client. Remove `googleIosClientId` from your config; no replacement is needed. Native
  **Apple** sign-in is unaffected — `appleNativeLogin` resolves through `AuthenticationServices`, a
  system framework.

- **BREAKING for implementors of `KmpSupabaseAuthClient`** — the interface gains
  `supportsNativeGoogle`. Consumers that only *use* the client are unaffected; a test double or custom
  implementation must add the member.

- The `GOOGLE native SKIPPED` log line now names the real reason. On iOS it reported
  `googleWebClientId is set(72 chars)`, which reads as "your client id is the problem" when the client
  id is irrelevant there — a log that sends someone to fix the wrong field is worse than no log.

### Fixed

- **A successful OAuth sign-in could be silently discarded on iOS, reported as a user cancellation.**
  supabase-kt defaults to `FlowType.IMPLICIT` (`AuthConfigDefaults` initializes it so), which returns
  the session in the callback URL's **fragment** (`#access_token=…&refresh_token=…`), not as a PKCE
  `code` in the query. The iOS handler searched only the query, found nothing, and threw `Cancelled` —
  indistinguishable from the person dismissing the sheet. Observed on a device as "sign-in completes at
  Google and then nothing happens". Both shapes are now handled, rather than pinning a flow type, since
  the flow is the consumer's choice: a `code` is exchanged, `access_token`+`refresh_token` are imported
  (with `retrieveUser = true`, because the implicit callback returns tokens only and the session would
  otherwise be authenticated with a null user), an `error`/`error_description` in either half surfaces
  as `ProviderRejected` carrying the provider's own message, and a genuinely malformed callback reports
  a distinct error instead of `Cancelled`.

- `ASWebAuthenticationSession` failures are no longer all reported as cancellations. The session's
  `NSError` domain and code are logged, so `canceledLogin` (the person tapped Cancel) is
  distinguishable from `presentationContextNotProvided` (nothing was ever presented), and
  `start()` returning false — the sheet never appearing — is reported instead of being silent.

- The Apple logo no longer distorts: its `ImageVector` declared a `24x24` default against a `384x512`
  path viewport, and the glyph is now sized to the mark's real aspect ratio.

## [0.2.0] - 2026-10-01

### Added

- `kmpSupabaseAuthInstall(config)` — everything the library installs into a Supabase client, as ONE
  branch of a fork's `SupabaseExtrasProvider`, replacing the two-call
  `kmpSupabaseAuthExtras` + `kmpSupabaseComposeAuthExtras` form every consumer had to write by hand and
  get in the right order. It composes INTO the fork's provider rather than binding one: the
  template resolves exactly one `SupabaseExtrasProvider` and a fork may branch it across several
  access points, so a library-owned binding would collide (`DefinitionOverrideException`) and take
  the extension point away from the only place that sees them all.

- `docs/INTEGRATE_KMP_TEMPLATE.md` — layer-by-layer guide for wiring the library into a
  `kmp-project-template` fork (`core/network` → `core/store` → `core/data` → `feature/auth`),
  written from a real migration rather than from the API surface. Covers the `SupabaseExtrasProvider`
  seam, why `createSupabaseClient` must never be called, the three-state session model and the
  `!isAuthenticated` predicate UI actually wants, the native-vs-web matrix with its silent
  `googleWebClientId` fallback, and the device verification a compiling app does not prove.

### Changed

- **BREAKING — every public type and function now carries the `KmpSupabaseAuth*` / `kmpSupabaseAuth*`
  prefix.** The library's domain types were previously bare (`AuthSession`, `AuthUser`,
  `AuthRepository`, `AuthError`, `AuthPhase`, `AuthSessionStore`), which collides with the names an
  app naturally gives its own auth layer. Measured on a real consumer: the SAME library type was
  imported under THREE different aliases across four files (`as SupabaseAuth`,
  `as SupabaseAuthRepository`, and again `as SupabaseAuth`) purely to dodge the clash. After this
  change that consumer imports every symbol directly and declares no alias at all.

  | before | after |
  |---|---|
  | `AuthSession` · `AuthUser` · `AuthError` · `AuthProvider` · `AuthPhase` | `KmpSupabaseAuthSession` · `KmpSupabaseAuthUser` · `KmpSupabaseAuthError` · `KmpSupabaseAuthProvider` · `KmpSupabaseAuthPhase` |
  | `AuthRepository` · `AuthSessionStore` | `KmpSupabaseAuthRepository` · `KmpSupabaseAuthSessionStore` |
  | `SupabaseAuthClient` · `SupabaseAuthConfig` · `SupabaseAuthLog` · `SupabaseAuthOptions` | `KmpSupabaseAuthClient` · `KmpSupabaseAuthConfig` · `KmpSupabaseAuthLog` · `KmpSupabaseAuthOptions` |
  | `SupabaseAuthViewModel` · `SupabaseAuthUiState` | `KmpSupabaseAuthViewModel` · `KmpSupabaseAuthUiState` |
  | `SupabaseSignInButton` · `GoogleSignInButton` · `AppleSignInButton` · `SignInLauncher` | `KmpSupabaseSignInButton` · `KmpSupabaseGoogleSignInButton` · `KmpSupabaseAppleSignInButton` · `KmpSupabaseSignInLauncher` |
  | `rememberSignIn` · `rememberGoogleSignIn` · `rememberAppleSignIn` | `rememberKmpSupabaseSignIn` · `rememberKmpSupabaseGoogleSignIn` · `rememberKmpSupabaseAppleSignIn` |
  | `supabaseAuthNetwork` · `supabaseAuthStore` · `supabaseAuthRepository` · `supabaseAuth` · `kmpSupabaseAuthInstall` | `kmpSupabaseAuthNetwork` · `kmpSupabaseAuthStore` · `kmpSupabaseAuthRepository` · `kmpSupabaseAuth` · `kmpSupabaseAuthInstall` |

  Artifact coordinates are unchanged (`io.github.mobilebytelabs:cmp-supabase-auth`), as is the
  package (`io.github.mobilebytelabs.supabaseauth`). Only the symbol names move.

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
