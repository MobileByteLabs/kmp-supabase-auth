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

- `KmpSupabaseAuthError`
- `KmpSupabaseAuthProvider`
- `KmpSupabaseAuthRepository`
- `KmpSupabaseAuthSession`
- `KmpSupabaseAuthSessionStore`
- `KmpSupabaseAuthUser`
- `FakeKmpSupabaseAuthRepository`
- `KmpSupabaseAuth`
- `KmpSupabaseAuthClient`
- `KmpSupabaseAuthConfig`
- `KmpSupabaseAuthOptions`

**`cmp-supabase-auth-compose`**

- `KmpSupabaseProviderSignInPath`
- `KmpSupabaseSignInLauncher`
- `KmpSupabaseSignInPath`
- `KmpSupabaseSignInPathReport`
- `KmpSupabaseAuthUiState`
- `KmpSupabaseAuthViewModel`
<!-- docs-gen:api:end -->

## cmp-supabase-auth

### Configuration

| Type | Purpose |
|---|---|
| `KmpSupabaseAuthConfig` | `projectRef`, `googleWebClientId`, `googleIosClientId`, `appleServiceId`, `redirectUrl`; derived `hasGoogleNative`, `oauthScheme`, `oauthHost`, `isConfigured` |
| `KmpSupabaseAuthOptions` | `extraInstall {}`, `userMapper {}`, `onSessionChanged {}`, `googleNative(Boolean)`, `appleNative(Boolean)` |
| `KmpSupabaseAuth` | `validate(config)` — fails fast on a blank or placeholder `projectRef` |

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
| `KmpSupabaseAuthClient` | `core/network` — `sessionStatus`, `currentUser`, `isSignedIn`, `signInAnonymously`, `signInWith*Fallback`, `hasRestorableSession`, `currentAccessToken`, `signOut`, `raw` |
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
