# API surface

Every public declaration lives in **commonMain**. Generated API dumps are committed under
`cmp-supabase-auth*/api/` and enforced by Binary Compatibility Validator — a public API change
without a regenerated baseline fails CI.

```bash
./gradlew apiDump   # after an intentional API change
./gradlew apiCheck  # what CI runs
```

<!-- docs-gen:api:begin -->
**`cmp-supabase-auth`**

- `FakeKmpSupabaseAuthRepository`
- `KmpSupabaseAuth`
- `KmpSupabaseAuthClient`
- `KmpSupabaseAuthConfig`
- `KmpSupabaseAuthError`
- `KmpSupabaseAuthLog`
- `KmpSupabaseAuthOptions`
- `KmpSupabaseAuthProvider`
- `KmpSupabaseAuthRepository`
- `KmpSupabaseAuthSession`
- `KmpSupabaseAuthSessionStore`
- `KmpSupabaseAuthUser`

**`cmp-supabase-auth-compose`**

- `KmpSupabaseAppleButtonStyle`
- `KmpSupabaseAuthPhase`
- `KmpSupabaseAuthState`
- `KmpSupabaseAuthUiState`
- `KmpSupabaseAuthViewModel`
- `KmpSupabaseProviderSignInPath`
- `KmpSupabaseSignInAction`
- `KmpSupabaseSignInLauncher`
- `KmpSupabaseSignInPath`
- `KmpSupabaseSignInPathReport`
<!-- docs-gen:api:end -->

## cmp-supabase-auth

### Configuration

| Type | Purpose |
|---|---|
| `KmpSupabaseAuthConfig` | `projectRef`, `googleWebClientId`, `appleServiceId`, `redirectUrl`; derived `hasGoogleNative`, `oauthScheme`, `oauthHost`, `isConfigured`. **`googleIosClientId` removed in 0.3.0** — iOS has no native Google path a published library can deliver |
| `KmpSupabaseAuthOptions` | `extraInstall {}`, `userMapper {}`, `onSessionChanged {}`, `googleNative(Boolean)`, `appleNative(Boolean)` |
| `KmpSupabaseAuth` | `validate(config)` — fails fast on a blank or placeholder `projectRef` |

### Added in 0.3.0

| Member | Purpose |
|---|---|
| `KmpSupabaseAuthClient.supportsNativeGoogle` | Whether this platform can run native Google with only what the library ships. **`false` on iOS** — the native path needs the GoogleSignIn SDK in the app's own Xcode/SPM graph, which Xcode resolves before Gradle runs. A *delivery* fact, independent of whether a client id is configured; see `KmpSupabaseAuthConfig.hasGoogleNative` for the configured half. The Compose launchers read it and route Google to the in-app web flow. |
| `KmpSupabaseAuthLog.silenced` | Force diagnostics off regardless of build type. |
| `awaitAppForegroundReturn()` | Suspends until the host app returns to the foreground. Public only because `internal` is module-scoped and the launchers live in `cmp-supabase-auth-compose` — **consumers have no reason to call it**; the launchers already race it against the no-response timeout to detect a cancelled provider. Real on Android (`ActivityLifecycleCallbacks`) and iOS (`UIApplicationDidBecomeActive`); a never-emitting no-op elsewhere. |

**Removed in 0.3.0:** `KmpSupabaseAuthConfig.googleIosClientId`. Delete it from your config — there is
no replacement, because iOS authenticates against the **Web** client. Also note
`KmpSupabaseAuthClient` gained an abstract member, so a custom implementation or test double must add
`supportsNativeGoogle`.

**Changed in 0.3.0:** `KmpSupabaseAuthLog` defaults to logging in **debug** builds
(`Platform.isDebugBinary` on native, the host app's `FLAG_DEBUGGABLE` on Android) instead of requiring
an explicit `handler`. It still logs decisions and outcomes only — never a token, id or email.

### Domain

| Type | Purpose |
|---|---|
| `KmpSupabaseAuthUser` | `id`, `email`, `displayName`, `avatarUrl`, `provider`, `isAnonymous` |
| `KmpSupabaseAuthProvider` | `GOOGLE`, `APPLE`, `ANONYMOUS`, `EMAIL`, `OTHER` |
| `KmpSupabaseAuthError` | `Cancelled`, `Network`, `ProviderRejected`, `NotConfigured`, `Unknown` |

`KmpSupabaseAuthError.Cancelled` is **not** a failure — a person dismissing the provider sheet has not hit a
problem, and the ViewModel deliberately does not surface it.

### Layers

| Type | Rung |
|---|---|
| `KmpSupabaseAuthClient` | `core/network` — `sessionStatus`, `currentUser`, `isSignedIn`, `signInAnonymously`, `signInWith*Fallback`, `hasRestorableSession`, `currentAccessToken`, `signOut`, `raw`, **`supportsNativeGoogle`** |
| `KmpSupabaseAuthSessionStore` | `core/store` — `user`, `isSignedIn`, `start(scope)`, `clear()` |
| `KmpSupabaseAuthRepository` | `core/data` — `currentUser`, `isSignedIn`, `accessTokenFlow`, `continueAsGuest`, `signInWithFallback`, `signOut`, `restoreSession`, `accessToken` |

### DI

| Function | Binds |
|---|---|
| `kmpSupabaseAuthExtras(config)` | the `Auth` install block |
| `kmpSupabaseAuthNetwork(config, configure, clientProvider)` | `KmpSupabaseAuthClient`, `KmpSupabaseAuthOptions` |
| `kmpSupabaseAuthStore()` | `KmpSupabaseAuthSessionStore` |
| `kmpSupabaseAuthRepository()` | `KmpSupabaseAuthRepository` |
| `kmpSupabaseAuth(config, …)` | all three above |

### Testing

`FakeKmpSupabaseAuthRepository` ships in the **main** artifact, not a test source set, so your app modules
can use it. Drive it with `emitSession(user)` to simulate GoTrue pushing a session.

## cmp-supabase-auth-compose

| Declaration | Purpose |
|---|---|
| `kmpSupabaseComposeAuthExtras(config, googleNative, appleNative)` | the `ComposeAuth` install block |
| `rememberKmpSupabaseGoogleSignIn(client, linkIdentity, onError)` | native Google; `linkIdentity = true` upgrades a guest |
| `rememberKmpSupabaseAppleSignIn(client, linkIdentity, onError)` | native Apple on iOS, redirect elsewhere |
| `KmpSupabaseSignInLauncher` | `launch()` |
| `KmpSupabaseGoogleSignInButton` / `KmpSupabaseAppleSignInButton` / `KmpSupabaseContinueAsGuestButton` | brand-compliant buttons |
| `KmpSupabaseLoginScreen(...)` | slot-based screen — `header`, `footer`, `showGoogle/Apple/GuestOption`, `onSignedIn` |
| `KmpSupabaseAuthViewModel` | `state: StateFlow<KmpSupabaseAuthUiState>`, `onSignInStarted`, `onSignInFailed`, `continueAsGuest`, `signInWithFallback`, `signOut`, `dismissError` |
| `KmpSupabaseAuthUiState` | `isLoading`, `user`, `isSignedIn`, `error` |
| `kmpSupabaseAuthComposeModule()` | binds the ViewModel as a **factory** |

A `factory`, not a `single`: each login screen gets its own ViewModel, so a sign-in abandoned on
one screen cannot leave stale loading or error state on the next.
