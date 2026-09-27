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

- `AuthError`
- `AuthProvider`
- `AuthRepository`
- `AuthSessionStore`
- `AuthUser`
- `FakeAuthRepository`
- `SupabaseAuth`
- `SupabaseAuthClient`
- `SupabaseAuthConfig`
- `SupabaseAuthOptions`

**`cmp-supabase-auth-compose`**

- `ProviderSignInPath`
- `SignInLauncher`
- `SignInPath`
- `SignInPathReport`
- `SupabaseAuthUiState`
- `SupabaseAuthViewModel`
<!-- docs-gen:api:end -->

## cmp-supabase-auth

### Configuration

| Type | Purpose |
|---|---|
| `SupabaseAuthConfig` | `projectRef`, `googleWebClientId`, `googleIosClientId`, `appleServiceId`, `redirectUrl`; derived `hasGoogleNative`, `oauthScheme`, `oauthHost`, `isConfigured` |
| `SupabaseAuthOptions` | `extraInstall {}`, `userMapper {}`, `onSessionChanged {}`, `googleNative(Boolean)`, `appleNative(Boolean)` |
| `SupabaseAuth` | `validate(config)` — fails fast on a blank or placeholder `projectRef` |

### Domain

| Type | Purpose |
|---|---|
| `AuthUser` | `id`, `email`, `displayName`, `avatarUrl`, `provider`, `isAnonymous` |
| `AuthProvider` | `GOOGLE`, `APPLE`, `ANONYMOUS`, `EMAIL`, `OTHER` |
| `AuthError` | `Cancelled`, `Network`, `ProviderRejected`, `NotConfigured`, `Unknown` |

`AuthError.Cancelled` is **not** a failure — a person dismissing the provider sheet has not hit a
problem, and the ViewModel deliberately does not surface it.

### Layers

| Type | Rung |
|---|---|
| `SupabaseAuthClient` | `core/network` — `sessionStatus`, `currentUser`, `isSignedIn`, `signInAnonymously`, `signInWith*Fallback`, `hasRestorableSession`, `currentAccessToken`, `signOut`, `raw` |
| `AuthSessionStore` | `core/store` — `user`, `isSignedIn`, `start(scope)`, `clear()` |
| `AuthRepository` | `core/data` — `currentUser`, `isSignedIn`, `accessTokenFlow`, `continueAsGuest`, `signInWithFallback`, `signOut`, `restoreSession`, `accessToken` |

### DI

| Function | Binds |
|---|---|
| `supabaseAuthExtras(config)` | the `Auth` install block |
| `supabaseAuthNetwork(config, configure, clientProvider)` | `SupabaseAuthClient`, `SupabaseAuthOptions` |
| `supabaseAuthStore()` | `AuthSessionStore` |
| `supabaseAuthRepository()` | `AuthRepository` |
| `supabaseAuth(config, …)` | all three above |

### Testing

`FakeAuthRepository` ships in the **main** artifact, not a test source set, so your app modules
can use it. Drive it with `emitSession(user)` to simulate GoTrue pushing a session.

## cmp-supabase-auth-compose

| Declaration | Purpose |
|---|---|
| `supabaseComposeAuthExtras(config, googleNative, appleNative)` | the `ComposeAuth` install block |
| `rememberGoogleSignIn(client, linkIdentity, onError)` | native Google; `linkIdentity = true` upgrades a guest |
| `rememberAppleSignIn(client, linkIdentity, onError)` | native Apple on iOS, redirect elsewhere |
| `SignInLauncher` | `launch()` |
| `GoogleSignInButton` / `AppleSignInButton` / `ContinueAsGuestButton` | brand-compliant buttons |
| `SupabaseLoginScreen(...)` | slot-based screen — `header`, `footer`, `showGoogle/Apple/GuestOption`, `onSignedIn` |
| `SupabaseAuthViewModel` | `state: StateFlow<SupabaseAuthUiState>`, `onSignInStarted`, `onSignInFailed`, `continueAsGuest`, `signInWithFallback`, `signOut`, `dismissError` |
| `SupabaseAuthUiState` | `isLoading`, `user`, `isSignedIn`, `error` |
| `supabaseAuthComposeModule()` | binds the ViewModel as a **factory** |

A `factory`, not a `single`: each login screen gets its own ViewModel, so a sign-in abandoned on
one screen cannot leave stale loading or error state on the next.
