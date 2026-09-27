# Install &amp; wire up

## 1. Add the dependencies

<!-- docs-gen:install:begin -->
```kotlin
dependencies {
    implementation("io.github.mobilebytelabs:cmp-supabase-auth:0.1.0")  // headless
    implementation("io.github.mobilebytelabs:cmp-supabase-auth-compose:0.1.0")  // Compose UI (optional)
}
```
<!-- docs-gen:install:end -->

The Compose module brings the headless one transitively (`api`), so a UI app can take just it.

## 2. Configure

```kotlin
val authConfig = SupabaseAuthConfig(
    projectRef        = "your-project-ref",
    googleWebClientId = BuildKonfig.GOOGLE_OAUTH_WEB_CLIENT_ID,
    redirectUrl       = "myapp://login-callback",
)
```

> **Use the Google Cloud _Web_ client id** — not the Android or iOS one. Both GoTrue and the
> Android Credential Manager verify against the Web id, and supplying the Android id fails at
> runtime with an opaque provider error. See [Google Sign-In](SETUP_GOOGLE.md).

Leaving a provider id blank is safe: the library skips that native flow and falls back to the
OAuth redirect. Only a blank or placeholder `projectRef` is fatal — without it there is no client
to install onto.

## 3. Install the plugins on your existing client

The library **never calls `createSupabaseClient`**. It installs onto the client your app already
has — in a kmp-project-template fork, the one your Supabase training layer codegens into
`core/network`.

```kotlin
// core/network/di/ProjectNetworkModule.kt  — owner:fork, survives template sync
single<SupabaseExtrasProvider> {
    SupabaseExtrasProvider { id ->
        when (id) {
            authConfig.projectRef -> {
                {
                    supabaseAuthExtras(authConfig)(this)         // Auth
                    supabaseComposeAuthExtras(authConfig)(this)  // ComposeAuth (native sign-in)
                }
            }
            else -> { {} }
        }
    }
}
```

?> Two blocks because `compose-auth` is a Compose artifact. Keeping it out of the headless module
is what lets `cmp-supabase-auth` reach 17 targets instead of 7 — see [Target matrix](../TARGET_MATRIX.md).

## 4. Wire the DI

One line:

```kotlin
includes(supabaseAuth(authConfig))
```

…or one per layer, if you prefer each rung where it belongs:

```kotlin
includes(supabaseAuthNetwork(authConfig))  // core/network
includes(supabaseAuthStore())              // core/store
includes(supabaseAuthRepository())         // core/data
includes(supabaseAuthComposeModule())      // feature
```

`supabaseAuth(config)` is *defined as* the first three, and a test asserts the binding sets are
identical — the two forms cannot drift apart.

**On kmp-project-template**, the client comes from a factory rather than a direct binding, so pass
the lookup:

```kotlin
includes(
    supabaseAuth(authConfig) {
        get<SupabaseClientFactory>().requireClientFor(authConfig.projectRef).client
    },
)
```

## 5. Bridge the token to your API calls

This is the step that silently breaks RLS if skipped. The library exposes the JWT; the template's
existing `AuthHeaderBridge` does the rest:

```kotlin
single<AuthTokenSource> {
    val repository = get<AuthRepository>()
    AuthTokenSource { _ -> repository.accessTokenFlow }
}
```

## 6. Show the screen

```kotlin
SupabaseLoginScreen(
    viewModel = koinViewModel(),
    client = koinInject(),
    header = { YourLogo() },
    onSignedIn = { navigateHome() },
)
```

Or compose your own from the parts:

```kotlin
val google = rememberGoogleSignIn(client, onError = viewModel::onSignInFailed)
GoogleSignInButton(onClick = { viewModel.onSignInStarted(); google.launch() })
ContinueAsGuestButton(onClick = viewModel::continueAsGuest)
```

!> **Never treat the provider callback as success.** On Android `NativeSignInResult.Success`
frequently never fires even though the session landed. Observe `AuthRepository.isSignedIn`
instead — `SupabaseAuthViewModel` already does.

## 7. Guest sessions

```kotlin
viewModel.continueAsGuest()                       // anonymous session, RLS applies immediately
rememberGoogleSignIn(client, linkIdentity = true) // upgrade, KEEPING the user id
```

`linkIdentity` preserves the id, so no guest data has to be migrated. It works on the same
install; a guest signing in on a second device gets a different id.

## Next

- [Google Sign-In setup](SETUP_GOOGLE.md) · [Sign in with Apple setup](SETUP_APPLE.md)
- [Architecture](architecture.md) — why it is shaped this way
