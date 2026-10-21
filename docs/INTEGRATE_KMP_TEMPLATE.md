# Integrating with kmp-project-template

How to wire this library into an app forked from
[`kmp-project-template`](https://github.com/openMF/kmp-project-template), layer by layer:
**`core/network` → `core/store` → `core/data` → `feature/auth`**.

Every step below was performed on a real fork before being written down. Where something bit, the
guide says so rather than describing the happy path only.

> **The library is not added to the template.** Not every app needs auth, so the template stays
> neutral and a fork opts in. Nothing here requires a template change.

---

## Why bottom-up

Each layer only depends on the one beneath it, so building upward means every step compiles before
the next begins. Going the other way — starting at the UI — leaves you holding a sign-in screen
with no session behind it, and a failure at the bottom then surfaces as a confusing error at the top.

| Step | Layer | What it gains | Can you stop here? |
|---|---|---|---|
| 0.5 | `app-profile/` | the access point + its secrets exist and generate | No — nothing below works |
| 1 | `core/network` | the client has `Auth` + `ComposeAuth` installed | Yes — nothing consumes it yet |
| 2 | `core/store` | session state as a `StateFlow` | Yes |
| 3 | `core/data` | a repository your app's own code can depend on | Yes — headless apps stop here |
| 4 | `feature/auth` | sign-in UI | Yes — if nothing else shows account state |
| 5 | `cmp-navigation` | the app shell reads the session (account row, sign-out) | Yes |
| 6 | platform | OAuth callback + debug diagnostics | Yes — native-only apps may skip the callback |
| 7 | server | identity row, trigger, RLS — the half that makes sign-in *useful* | **No** |

Step 7 is the one that cannot be skipped and is most often missed: every client-side step can be
perfect while a signed-in user lands in an empty app, because the provider reports `enabled` and
GoTrue happily creates a user with nowhere to put it.

---

## Prerequisite: the template's seam

The template ships `SupabaseExtrasProvider` in `core-base/network`. `SupabaseConfigClient` installs
Postgrest and nothing else; `Auth` and `ComposeAuth` are opt-in, and this fun-interface is how a
fork asks for them:

```kotlin
fun interface SupabaseExtrasProvider {
    fun forId(id: String): SupabaseClientBuilder.() -> Unit
}
```

**Never call `createSupabaseClient` yourself.** A second client carries no session, so the generated
`supabaseApi(...)` binding hands `@ApiBinding` types an unauthenticated instance and every
RLS-gated call resolves no `auth.uid()` — while compiling cleanly. The template's own KDoc warns
about this; the library exists partly to make the correct path the easy one.

---

## Upgrading from 0.1.x

Every public symbol now carries the `KmpSupabaseAuth*` / `kmpSupabaseAuth*` prefix — see the
[CHANGELOG](/CHANGELOG.md) for the release that introduced it. Artifact coordinates
(`io.github.mobilebytelabs:cmp-supabase-auth`) and the package
(`io.github.mobilebytelabs.supabaseauth`) are unchanged — only symbol names move, so the upgrade is
a mechanical find-and-replace with no dependency edit.

| before | after |
|---|---|
| `AuthSession` · `AuthUser` · `AuthError` · `AuthProvider` · `AuthPhase` | `KmpSupabaseAuthSession` · `…User` · `…Error` · `…Provider` · `…Phase` |
| `AuthRepository` · `AuthSessionStore` | `KmpSupabaseAuthRepository` · `KmpSupabaseAuthSessionStore` |
| `SupabaseAuthClient` · `SupabaseAuthConfig` · `SupabaseAuthLog` | `KmpSupabaseAuthClient` · `…Config` · `…Log` |
| `SupabaseSignInButton` · `GoogleSignInButton` · `AppleSignInButton` | `KmpSupabaseSignInButton` · `KmpSupabaseGoogleSignInButton` · `KmpSupabaseAppleSignInButton` |
| `rememberSignIn` · `rememberGoogleSignIn` | `rememberKmpSupabaseSignIn` · `rememberKmpSupabaseGoogleSignIn` |
| `supabaseAuthNetwork` · `supabaseAuthStore` · `supabaseAuthRepository` | `kmpSupabaseAuthNetwork` · `kmpSupabaseAuthStore` · `kmpSupabaseAuthRepository` |

The reason is collision: the bare names are exactly what an app calls its own auth layer. In a real
consumer the SAME library type was imported under THREE different aliases across four files purely
to dodge the clash; after the rename it declares none.

**Two behaviour changes come with it**, both covered in Step 4 and neither caught by the compiler:

- the provider buttons are **self-wired** — they no longer take a launcher you built, and they must
  stay MOUNTED during sign-in;
- `onClick` is no longer the first positional parameter, so an existing positional call will not
  compile — which is the good case. Check every `onError` is passed.

---

## Step 0 — Dependencies

```kotlin
// gradle/libs.versions.toml
[versions]
cmpSupabaseAuth = "<see the Maven Central badge on the README>"

[libraries]
cmp-supabase-auth = { module = "io.github.mobilebytelabs:cmp-supabase-auth", version.ref = "cmpSupabaseAuth" }
cmp-supabase-auth-compose = { module = "io.github.mobilebytelabs:cmp-supabase-auth-compose", version.ref = "cmpSupabaseAuth" }
```

```kotlin
repositories {
    mavenCentral()
    google() // required by -compose only; see below
}
```

`cmp-supabase-auth-compose` pulls in Compose Multiplatform, whose transitive AndroidX dependencies
(`androidx.savedstate`, `androidx.lifecycle-*`) are published **only** to Google's Maven repository.
The headless module resolves from `mavenCentral()` alone.

---

## Step 0.5 — `app-profile/` : declare the access point and its secrets

This is the true bottom of the chain, and it is template machinery rather than library API — but
`core/network` cannot be wired without it, so it comes first.

The template declares every endpoint ONCE in `app-profile/app.yaml`, and `./gradlew syncForkConfig`
generates `AppAccessPoints` + `AppSupabaseAnonKeys` from it. You never hand-write a client, a URL or
a key:

```yaml
# app-profile/app.yaml
network:
  access_points:
    - id: <your-supabase-project-ref>     # this id IS the access-point key used in Step 1
      type: supabase
      base_url: https://<ref>.supabase.co
```

```bash
./gradlew syncForkConfig      # regenerates AppAccessPoints / AppSupabaseAnonKeys
```

The credentials come from the vault, not from the YAML — `<proj>-supabase-url`,
`<proj>-supabase-anon-key`, plus `<proj>-google-oauth-web-client-id` for Google. Materialize with
`/secrets pull`. Two traps worth naming:

- **The URL and the anon key must belong to the SAME project.** The anon key is a JWT whose `ref`
  claim names its project; pairing a new URL with an old key yields `Invalid API key` at startup
  and nothing more specific. Decode the `ref` and compare — it is public, unlike the key itself.
- **`.env` files are not the mechanism here.** Secrets materialize to `local.properties` /
  `secrets/live/**` via the layout; a hand-written `.env` will be ignored by the build and is a
  gate violation besides.

---

## Step 1 — `core/network`

Two things happen here: the extras get installed into the client, and the library's DI graph is
registered.

```kotlin
// core/network/build.gradle.kts
commonMain.dependencies {
    // `api`, not `implementation` — core/data re-exposes KmpSupabaseAuthUser/KmpSupabaseAuthSession upward.
    api(libs.cmp.supabase.auth)
    // The seam installs ComposeAuth too, and kmpSupabaseComposeAuthExtras lives in the Compose module.
    implementation(libs.cmp.supabase.auth.compose)
}
```

### 1a. Fill the seam

```kotlin
// core/network/src/commonMain/kotlin/.../di/ProjectNetworkModule.kt
private const val ACCESS_POINT = "<your-supabase-project-ref>"

private val AuthConfig = KmpSupabaseAuthConfig(
    projectRef = ACCESS_POINT,
    googleWebClientId = YourConfig.googleOauthWebClientId, // the WEB client id, not Android/iOS
    redirectUrl = YourConfig.oauthRedirectUrl,             // e.g. "com.example.app://login-callback"
)

val ProjectNetworkModule = module {
    single<SupabaseExtrasProvider> {
        SupabaseExtrasProvider { id ->
            when (id) {
                ACCESS_POINT -> kmpSupabaseAuthInstall(AuthConfig) // Auth + ComposeAuth
                else -> { {} }
            }
        }
    }
}
```

`kmpSupabaseAuthInstall` is everything the library installs, as one branch: it splits `redirectUrl`
into the scheme/host GoTrue needs to intercept the callback, installs `googleNativeLogin` **only
when `googleWebClientId` is non-blank**, and installs `appleNativeLogin` unconditionally — see
[Native vs web](#native-vs-web-know-which-you-shipped). Use `kmpSupabaseAuthExtras` +
`kmpSupabaseComposeAuthExtras` separately only if you need Auth without the native providers.

### Keep auth in ONE place

The seam is the single place auth is wired, and the library composes **into** it rather than
binding its own provider. The template resolves exactly one instance —
`getOrNull<SupabaseExtrasProvider>()?.forId(id)` — and a fork may run several projects, so:

```kotlin
when (id) {
    AUTH_ACCESS_POINT -> kmpSupabaseAuthInstall(AuthConfig)
    ANALYTICS_POINT   -> { { install(Realtime) } }
    else              -> { {} }
}
```

A library that bound `single<SupabaseExtrasProvider>` itself would collide with that binding
(Koin raises `DefinitionOverrideException` on a duplicate type) and take the extension point away
from the one place that can see every access point. Name the config **`AuthConfig`**, not
`<Project>KmpSupabaseAuthConfig` — `KmpSupabaseAuthConfig` already says which library it belongs to, and
a project prefix makes the same integration read differently in every fork.

### 1b. Register the DI graph

```kotlin
includes(
    kmpSupabaseAuth(
        config = AuthConfig,
        clientProvider = { get<SupabaseClientFactory>().requireClientFor(ACCESS_POINT).client },
    ),
)
```

`kmpSupabaseAuth(...)` is *defined as* `kmpSupabaseAuthNetwork() + kmpSupabaseAuthStore() +
kmpSupabaseAuthRepository()`, so the three rungs cannot drift apart. Include them individually instead
if your fork keeps each in its own module.

Use `requireClientFor`, not `clientFor`. A null would hand the library a *different* client than
`@ApiBinding` types get — the two-client split described above, but silent. Fail loudly instead.

---

## Step 2 — `core/store`

```kotlin
commonMain.dependencies { api(libs.cmp.supabase.auth) }
```

`kmpSupabaseAuthStore()` (already included above) binds `KmpSupabaseAuthSessionStore`:

```kotlin
public interface KmpSupabaseAuthSessionStore {
    public val user: StateFlow<KmpSupabaseAuthUser?>
    public val isSignedIn: StateFlow<Boolean>
    public val session: StateFlow<KmpSupabaseAuthSession>
    public fun start(scope: CoroutineScope)
    public suspend fun clear()
}
```

Call `start(scope)` once, eagerly, at graph construction — it begins mirroring GoTrue's session.

**Do not wrap this in Store5.** The store is memory-only by design: GoTrue already persists and
refreshes the session, so a second cache would be a second source of truth with its own staleness.
If your fork has an existing Store5 `authSession` store, it becomes dead code at Step 3 — delete it
along with its `@StoreProvider`/`@CacheKey` annotations.

---

## Step 3 — `core/data`

```kotlin
commonMain.dependencies {
    api(libs.cmp.supabase.auth)
    implementation(libs.cmp.supabase.auth.compose) // only if you re-expose composeAuth
}
```

If your app already has its own `KmpSupabaseAuthRepository`, **keep that interface** and reimplement it over
the library's. Everything above `core/data` then stays untouched.

```kotlin
@RepositoryBinding(binds = KmpSupabaseAuthRepository::class)
internal class AuthRepositoryImpl(
    private val auth: SupabaseAuthRepository,   // aliased: io.github.mobilebytelabs...KmpSupabaseAuthRepository
    private val client: KmpSupabaseAuthClient,
) : KmpSupabaseAuthRepository {

    override val session: Flow<AppSession> = auth.session.map { it.toAppSession() }
    override val isConfigured: Boolean get() = client.isConfigured
    override suspend fun signOut() = auth.signOut()
    override fun currentAccessToken(): String? = auth.accessToken()
}

// Identity → your profile type. ONE place, so the library stays profile-agnostic.
private fun KmpSupabaseAuthUser.toAppProfile() = AppUserProfile(
    email = email, name = displayName, avatarUrl = avatarUrl,
)
```

### The library owns identity, your app owns everything else

`KmpSupabaseAuthUser` is `id`, `email`, `displayName`, `avatarUrl`, `provider`, `isAnonymous` — and stops
there. Profile rows, preferences and **entitlements** stay with the app.

If you need "is this user a subscriber?" alongside the session, derive it at read time rather than
storing it on the profile:

```kotlin
override val session: Flow<AppSession> =
    combine(auth.session, billing.entitlementStream()) { s, entitlement ->
        AppSession(user = s.user?.toAppProfile(), isPremium = entitlement.isPlus)
    }
```

A copy carried on the auth session refreshes only at sign-in, so a mid-session upgrade leaves a
paying subscriber looking free until they sign out and back in.

---

## Step 4 — `feature/auth`

```kotlin
commonMain.dependencies {
    implementation(libs.cmp.supabase.auth)
    implementation(libs.cmp.supabase.auth.compose)
}
```

The three buttons are **self-wired**: pressing one runs the whole flow. You supply a typed action
for your own state machine and nothing else — no launcher to remember, no client to thread, no
scope to own. `client` and `repository` default to `koinInject()`.

```kotlin
@Composable
fun AuthRoute(onError: (KmpSupabaseAuthError) -> Unit) {
    // Owned by the SCREEN, not the button — see "Keep the buttons mounted" below.
    val authScope = rememberCoroutineScope()

    KmpSupabaseGoogleSignInButton(scope = authScope, onError = onError)
    KmpSupabaseAppleSignInButton(scope = authScope, onError = onError)
    KmpSupabaseContinueAsGuestButton(onError = onError)
}
```

That is the whole integration. `onClick` is optional on all three — it is a hook for the app's own
side effects (a typed action for your state machine, an analytics event, a local "proceed"), and it
runs BEFORE the flow starts.

<details>
<summary>Example — wired to an MVI state machine (<code>mbs/cappy</code>)</summary>

```kotlin
KmpSupabaseGoogleSignInButton(
    onClick = { onAction(SignInAction.SignInGoogle) },   // app's own typed action
    modifier = Modifier.testTag(TestTags.Login.GOOGLE_BUTTON),
    enabled  = cloudConfigured && !signingIn,            // stays MOUNTED while signing in
    scope    = authScope,
    onError  = onAuthError,                              // maps to Dismissed / Offline / Failed
)
KmpSupabaseContinueAsGuestButton(
    onClick   = { onAction(SignInAction.ContinueOffline) },  // local-first: runs FIRST
    prominent = true,                                        // app's brand call, themed by MaterialTheme
    onError   = onAuthError,
)
```

`onAuthError` is the app's mapping from the library's error taxonomy onto its own states:

```kotlin
val onAuthError: (KmpSupabaseAuthError) -> Unit = { error ->
    viewModel.trySendAction(
        when (error) {
            is KmpSupabaseAuthError.Cancelled -> SignInAction.SignInDismissed
            is KmpSupabaseAuthError.Network   -> SignInAction.SignInOffline
            else                              -> SignInAction.SignInFailed
        },
    )
}
```

</details>

`KmpSupabaseSignInButton(provider)` dispatches to whichever of the three matches, including
`ANONYMOUS`. Or take the whole screen: `KmpSupabaseLoginScreen`, backed by
`KmpSupabaseAuthViewModel` (`state: StateFlow<KmpSupabaseAuthUiState>`), bound by
`kmpSupabaseAuthComposeModule()`.

### ALWAYS pass `onError` — or the screen hangs

`onError` defaults to a no-op, and that default is the single easiest way to ship a broken sign-in.
Every failure the library detects — a dismissed sheet (`Cancelled`), `Network`, `ProviderRejected`,
and the no-response watchdog's `NoResponse` — is reported THROUGH `onError`. Drop it and your
"signing in" state has no exit: the spinner runs forever with nothing in the logs.

This is not hypothetical. It shipped in a consumer, as "sometimes Google gets stuck on the progress
bar", because the error callback was defined and simply never passed to the buttons.

### Keep the buttons mounted while signing in

Each provider button remembers a `LaunchedEffect` (that is what `rememberSignInWithGoogle` installs).
**Swapping the buttons out for a spinner disposes that effect in the same recomposition that started
the flow** — `startFlow()` then runs against an effect that no longer exists, so no sheet appears, no
callback arrives, and the screen sits on "Signing in…" forever.

Render the same body for idle AND in-progress; disable the buttons instead of removing them:

```kotlin
when (state.phase) {
    Phase.Idle, Phase.SigningIn -> SignInBody(signingIn = state.phase == Phase.SigningIn)
    Phase.Error                 -> ErrorBody()
}
// inside SignInBody: enabled = !signingIn, with the progress shown alongside
```

`scope` is the related half: it is where the no-response watchdog runs, so it must outlive the
button for the same reason. Pass a screen-owned scope.

Guest and sign-out are deliberately different — they run on a DETACHED scope the library owns,
because pressing them usually navigates away and tears down the composition mid-request. The
provider watchdogs keep the composition scope, because those SHOULD die with their attempt.

### Reading who is signed in

Do not re-derive it. `rememberKmpSupabaseAuthState()` is the live read, resolved from DI:

```kotlin
val auth = rememberKmpSupabaseAuthState()
if (auth.hasNoAccount) SignInPrompt(onClick = { /* … */ })
```

| Read | True when |
|---|---|
| `isSignedIn` | any session — anonymous or a real account |
| `isAuthenticated` | a real provider account |
| `isGuest` | an **anonymous** session specifically — false when signed out |
| `hasNoAccount` | anonymous **OR** signed out — what a "sign in to save your progress" prompt wants |

The last two are the ones that get confused. `isGuest` alone hides the prompt from the
never-signed-in person it is most aimed at. Consumers were each writing
`authRepository.isAuthenticated.map { !it }.collectAsStateWithLifecycle(initialValue = true)` —
three decisions buried in one line, spelled differently at each call site, and the
`initialValue = true` renders a one-frame guest state for a signed-in user. `session` is a
`StateFlow` with a real current value, so no initial is needed.

Sign-out is wired too: `val signOut = rememberKmpSupabaseSignOut()`.

### Success is `isAuthenticated`, not the callback

On Android the native Google `onResult(Success)` callback **frequently never fires** even though
the exchange succeeded and the session landed — verified on-device. A UI waiting on it hangs on a
spinner while the person is already signed in.

Observe the session instead:

```kotlin
authRepository.session.collect { if (it.isAuthenticated) onSignedIn() }
```

`onError` is still worth wiring: `Cancelled`, `Network` and `ProviderRejected` do fire reliably.

---

## Step 5 — `cmp-navigation` : the app shell

The rung above the feature, and the one most often forgotten — the template's nav seam renders the
Settings account row and the sign-out control, so it reads auth state too.

Read it, do not re-derive it:

```kotlin
// any nav destination that shows an account row
val auth    = rememberKmpSupabaseAuthState()
val signOut = rememberKmpSupabaseSignOut()

SettingsScreen(
    hasNoAccount = auth.hasNoAccount,    // NOT !auth.isGuest — see "Three states"
    userName     = auth.user?.displayName,
    userEmail    = auth.user?.email,
    onSignOut    = { signOut() },
)
```

<details>
<summary>Example — the same seam in a real fork (<code>mbs/cappy</code>)</summary>

The template's registries are where a fork's destinations are declared, so this is where the read
lands in practice. Note the parameter is named `isGuest` by the screen but fed `hasNoAccount`: the
screen's question is "is there no real account here?", which is what that predicate answers.

```kotlin
// cmp-navigation/registry/BackboneRegistry.kt
val auth    = rememberKmpSupabaseAuthState()
val signOut = rememberKmpSupabaseSignOut()

CappyPreferencesScreen(
    isGuest         = auth.hasNoAccount,
    userName        = auth.user?.displayName,
    userEmail       = auth.user?.email,
    onSignOut       = { signOut() },
    onNavigateToSignIn = { navController.navigateToCloudSignIn() },
)
```

</details>

On a real migration this seam held the worst of the drift: two registries each injected the
repository and hand-derived `!isAuthenticated`, and the SAME library type was imported under three
different aliases across four files. One live read removes all of it.

A seam that is NOT composable (a `NavGraphBuilder` extension, where `remember*` cannot be called)
resolves lazily instead — `koin.get<KmpSupabaseAuthRepository>()` inside the lambda. That is the one
place reaching for the repository directly is still right.

---

## Step 6 — there is no platform step: it is fully commonMain

**Every line in Steps 0.5–5 is commonMain.** That is a deliberate property of the library, not a
coincidence of this guide:

| Module | commonMain | platform |
|---|---|---|
| `cmp-supabase-auth-compose` | 16 files — buttons, launchers, state, actions, ViewModel | **none** |
| `cmp-supabase-auth` | 16 files — client, repository, store, session, config, log | 2 (Android callback activity) + 1 no-op for everything else |

So the per-platform work is the LIBRARY's, behind `expect`/`actual`:

- `KmpSupabaseGoogleSignInButton` is one commonMain composable that resolves to Credential Manager
  on Android, native on iOS, and the GoTrue web redirect on desktop/web.
- The Android OAuth-callback activity ships **in the library's own `AndroidManifest.xml`** and
  merges into your app automatically — no manifest entry, no activity, no intent filter to declare.
  Other platforms get a no-op `actual` from `noCallbackMain`.

What you supply stays commonMain data: the scheme/host in `KmpSupabaseAuthConfig(redirectUrl = …)`,
matching a `uri_allow_list` entry on the backend. (Native Google and native Apple-on-iOS never use
the redirect; desktop, web and Android-Apple do.)

**Diagnostics are commonMain too.** `KmpSupabaseAuthLog.handler` is a commonMain property, so it can
be set in your shared init rather than per-platform:

```kotlin
// commonMain app init — `isDebug` from your own build config
if (isDebug) KmpSupabaseAuthLog.handler = { line -> println(line) }
```

Routing it to a platform logger (`Log.d` on Android, `os_log` on iOS) is a choice, not a
requirement — the only reason to touch a platform source set in this whole integration, and it is
optional. This is the single highest-value line when something goes wrong; see "When it hangs". It
logs decisions and outcomes, never credentials.

---

## Step 7 — the server half

Enabling a provider makes sign-in **succeed**; it does not make the app **work**. GoTrue creates the
`auth.users` row and stops. Your identity row, the RLS that scopes it, and the trigger that
provisions it are all server-side, and their absence is invisible from the client:

| Check | What breaks without it |
|---|---|
| identity table FK → `auth.users(id)` | sign-in creates a user with nowhere to land |
| `AFTER INSERT ON auth.users` trigger | orphaned users, permanently, invisibly |
| RLS + `auth.uid()` policies on every auth-scoped table | every signed-in user can read every row |
| anonymous enabled, if you offer guest | the guest button does nothing (GV-4b) |

Never substitute client-side row creation: a client that crashes or loses network between sign-in
and insert leaves an orphan forever, and every gate still passes. `/idea-auth` drives these as
SC-A..SC-E and can emit the trigger migration for you.

---

## Three states, not two

`KmpSupabaseAuthSession` distinguishes three cases, and conflating them causes real bugs:

| | `isSignedOut` | `isGuest` | `isAuthenticated` |
|---|---|---|---|
| nobody signed in | ✓ | | |
| anonymous session | | ✓ | |
| Google / Apple account | | | ✓ |

**`isGuest` means an anonymous session** — a real, upgradeable session whose id survives the
upgrade. It does **not** mean "nobody is signed in".

This matters because GoTrue reports an anonymous session as `Authenticated`. A fork that defines
guest as `sessionStatus !is Authenticated` will read a guest as fully signed in and show
"signed in as —" with no account behind it.

When UI wants *"there is no real account here"* — a sign-in prompt, a signed-out settings row —
that spans signed-out **and** anonymous, so the predicate is **`!isAuthenticated`**, never
`isGuest`. On a real migration, four call sites had `!isGuest` meaning "not signed in"; under the
correct semantics two became live bugs (a `SignInSucceeded` firing the moment the login screen
opened, and a cold-start data sync for signed-out users).

### The guest path needs the BACKEND switched on

`KmpSupabaseContinueAsGuestButton` calls `signInAnonymously()`, which fails unless the project
enables it:

```
Supabase → Authentication → Providers → Anonymous → enable
```

Read it back before trusting it — `external_anonymous_users_enabled` on
`GET /v1/projects/{ref}/config/auth`. Rate limiting is a separate setting
(`rate_limit_anonymous_users`), so enabling anonymous does not remove the abuse control.
`/idea-auth --verify` checks this as **GV-4b** for any app that offers a guest path.

Shipped with it disabled, the press does nothing visible: the session is refused and, with the
default no-op `onError`, nothing surfaces. Install the log handler (below) and you get
`ANONYMOUS: continueAsGuest() FAILED` naming the likely cause instead of silence.

### Local-first apps: `onClick` runs BEFORE the session request

The anonymous session is a network round-trip. If your app lets a guest in without one — the usual
local-first contract — put that local "proceed" in `onClick`, which the button runs *first*. The
guest then lands in the app with no network, and the anonymous session also lands when there is
one, which is what later allows the guest to be UPGRADED to a real account (`linkIdentity = true`)
rather than starting over.

---

## Native vs web: know which you shipped

| | Android | iOS |
|---|---|---|
| **Google** | native (Credential Manager) | **in-app web** (`ASWebAuthenticationSession`) |
| **Apple** | **in-app web** (Custom Tabs, in-task) | native (`ASAuthorizationController`) |

`googleWebClientId` must be **non-blank** for native Google on Android. Leave it blank and the library
skips `googleNativeLogin`, ComposeAuth sees a null config and falls back to the browser. Sign-in still
works, so nothing tells you — which is what `diagnoseKmpSupabaseSignInPaths` is for.

**iOS Google is web by design, and no SPM step will change that.** An earlier version of this guide
said to "add `GoogleSignIn-iOS` 9.0.0 via SPM" in the iOS app. Do not: supabase-kt's native iOS bridge
compiles against headers and links nothing, so supplying the SDK needs a declaration in a package
**Xcode resolves before any Gradle task runs** — unreachable from a Maven artifact. The first tap
aborts. `supportsNativeGoogle` is `false` on iOS and the launcher routes to the in-app session
instead. `googleIosClientId` was removed in 0.3.0.

**The web leg stays inside the app.** It is `ASWebAuthenticationSession` on iOS and a Custom Tab in
the app's own task on Android — *not* supabase-kt's `UIApplication.openURL`, which opens the external
Safari app and is what Apple rejects under **Guideline 4 - Design** ("the user is taken to the default
web browser to sign in"). A consuming app was rejected for exactly that before this was built.

Log the resolved paths at startup so a misconfiguration is visible before App Review is:

```kotlin
println(diagnoseKmpSupabaseSignInPaths(config).format())
// KmpSupabaseAuth sign-in paths on IOS:
//   GOOGLE -> WEB_FALLBACK (googleWebClientId is blank, so googleNativeLogin() is never installed …)
//   APPLE  -> NATIVE (ASAuthorizationController)
```

`KmpSupabaseSignInPathReport.webFallbacks` is assertable in a test, so "Google must be native on iOS" can be a
CI failure instead of a store rejection.

---

## Verifying the integration

A compiling app is not a working one. The DI graph changes materially here — new singles, a
different repository constructor — and Koin resolution failures surface at **runtime**.

```bash
./gradlew compileCommonMainKotlinMetadata   # types line up
./gradlew :androidApp:assembleDebug         # KSP/Koin codegen + manifest merge

adb install -r <apk>
adb shell am force-stop <applicationId>     # install -r does NOT kill a running process
adb shell am start -n <applicationId>/<launcher>
adb logcat -d | grep -iE "koin|NoBeanDefFound|InstanceCreationException"
```

The `force-stop` is not optional: without it a capture shows the previous composition and you get a
false pass.

### The sign-in itself: verify it LIVE, both sides

Compiling and resolving prove the graph. They say nothing about whether Google will hand you a
credential — that turns on config in **two consoles**, and the failure mode is silent. Run:

```bash
/idea-auth --verify --target <ws>/<project>
```

It reads Google Cloud and your backend live and reports `GV-1..GV-8` with the exact fix for each
failure. The checks that matter for Android, in the order they bite:

| # | Link | Gets this wrong and… |
|---|---|---|
| GV-1 | `serverClientId` is the **Web** client id | the Android id was pasted instead → the exchange is rejected |
| GV-3 | an **Android** OAuth client exists for your app id + **every** signing SHA-1 | Credential Manager returns nothing: no credential, no error, no callback — the app spins forever |
| GV-4 | the backend lists the **Android** client id as an accepted audience | the sheet succeeds, then GoTrue rejects the id_token — `onResult = Success` with no session |
| GV-5 | nonce handling matches the client | the exchange is rejected post-sheet |
| GV-7 | `auth.identities` holds a `google` row | proves no exchange has ever completed, whatever the config claims |

**The Web client and the Android client are both required and are not interchangeable.** The Web
client is what you pass as `serverClientId` and what carries the redirect URI; the Android client is
registered against `(package name, SHA-1)` and listed as an accepted audience. Neither alone works.

**Register a SHA-1 for every certificate that will run native Google** — debug, upload, *and* Play
App Signing. Play re-signs your upload, so a Play-track install can fail while your local build
passes. Get the fingerprints from the keystores, never by hand:

```bash
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore -storepass android | grep SHA1
```

### Apple: two audiences, and a secret with a shelf life

Apple's checks are `AV-1..AV-9` in the same `--verify` run. Two of them have no Google equivalent,
and both are things a one-time setup pass cannot catch:

**The client secret expires.** It is an ES256 JWT and Apple caps its lifetime at **6 months**. A
working Apple sign-in will break on a calendar date — no deploy, no code change, the provider still
reads `enabled`, and every exchange returns `invalid_client`. `AV-5` reads the expiry and warns at 30
days; re-minting is automatic (`/idea-auth` without `--verify`), so the only real failure mode is not
looking. Worth running on a schedule, not just at setup.

**Native iOS and the web fallback use different audiences.** Native sends your **bundle id**; the
web/Android round-trip sends your **Services ID**. `external_apple_client_id` must list both,
comma-separated. List one and it works on one platform family and fails on the other — so a green
manual test on an iPhone says nothing about Android, and an iOS-only project can carry a broken
Services ID indefinitely.

| # | Link | Gets this wrong and… |
|---|---|---|
| AV-1/2 | App ID **and** Services ID carry `APPLE_ID_AUTH` | native iOS fails at `ASAuthorizationController` |
| AV-3 | Services ID has the backend domain + return URL (console-only) | web fallback breaks; native unaffected |
| AV-4 | backend lists **both** bundle id and Services ID | fails on exactly one platform family |
| AV-5 | client secret unexpired | `invalid_client` on a date you did not choose |
| AV-6 | secret claims match team / Services ID / key id | also `invalid_client` — indistinguishable from expiry without this check |

On non-Apple platforms `signInWithFallback(KmpSupabaseAuthProvider.APPLE)` runs the GoTrue web round-trip, and
on iOS it opens a **Safari View Controller** rather than an embedded WebView — which is what Apple's
review guidance expects. Don't replace it with a custom WebView.

### When it hangs on "Signing in…"

Turn the library's own logging on first — it prints the decisions, never credentials:

```kotlin
if (BuildConfig.DEBUG) KmpSupabaseAuthLog.handler = { Log.d("KmpSupabaseAuth", it) }
```

Then read the trail:

| What you see | What it means |
|---|---|
| `install: GOOGLE native (serverClientId=blank)` | the id never reached the config |
| `startFlow()` then **nothing** | GV-3 — no Android client for this package + SHA-1 |
| `onResult = Success`, no session follows | GV-4/GV-5 — the audience list or nonce check rejected the token |
| `onResult = Error: …` | the provider's own reason is printed with the cause |
| `TIMEOUT after 60s` | nothing came back at all; treat as GV-3 |
| `startFlow()`, then `TIMEOUT`, and **no sheet ever appeared** | the buttons were unmounted mid-flow — see "Keep the buttons mounted" |
| `ANONYMOUS: continueAsGuest() FAILED` | usually anonymous sign-ins disabled on the project (GV-4b) |
| `ANONYMOUS: … FAILED … ForgottenCoroutineScopeException` | a composition scope was passed to the guest launcher and the press navigated away |
| nothing at all after pressing | `onError` was never passed — the library reported a failure into a no-op |

A guest who reads as "Signed in" with no name is a different fault: the session is anonymous but
something is flattening `isAnonymous`. Confirm server-side rather than guessing —
`select is_anonymous from auth.users` — then check that nothing in your mapping re-derives it.

Do **not** raise supabase-kt's own log level to `DEBUG` to chase this. It logs the entire
`UserSession`, access and refresh tokens included, straight into your terminal and any log
aggregator — a real credential leak that outlives the debugging session. `KmpSupabaseAuthLog` exists
precisely so you never have to.

---

## Related

- [Install &amp; wire up](/docs/getting-started.md)
- [Architecture](/docs/architecture.md)
- [API surface](/docs/api-reference.md)
- [Google Sign-In setup](/docs/SETUP_GOOGLE.md) · [Sign in with Apple setup](/docs/SETUP_APPLE.md)
