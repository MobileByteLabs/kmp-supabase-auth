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

| Layer | What it gains | Can you stop here? |
|---|---|---|
| `core/network` | the client has `Auth` + `ComposeAuth` installed | Yes — nothing consumes it yet |
| `core/store` | session state as a `StateFlow` | Yes |
| `core/data` | a repository your app's own code can depend on | Yes — headless apps stop here |
| `feature/auth` | sign-in UI | — |

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

```kotlin
@Composable
fun AuthRoute(client: KmpSupabaseAuthClient, onSignedIn: () -> Unit) {
    val google = rememberKmpSupabaseGoogleSignIn(client, onError = { /* surface it */ })
    val apple  = rememberKmpSupabaseAppleSignIn(client, onError = { /* surface it */ })

    AuthScreen(
        onGoogle = { google.launch() },
        onApple  = { apple.launch() },
    )
}
```

Or use `KmpSupabaseAuthViewModel` (`state: StateFlow<KmpSupabaseAuthUiState>` plus `continueAsGuest()`,
`signInWithFallback()`, `signOut()`, `dismissError()`), bound by `kmpSupabaseAuthComposeModule()`.

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

---

## Native vs web: know which you shipped

| | Android | iOS |
|---|---|---|
| **Google** | native (Credential Manager) | native (GoogleSignIn SDK) |
| **Apple** | web (browser OAuth) | native (`ASAuthorizationController`) |

Both native Google paths require `googleWebClientId` to be **non-blank**. Leave it blank and the
library skips `googleNativeLogin`, ComposeAuth sees a null config and silently falls back to the
browser. Sign-in still works, so nothing tells you.

Native Google on iOS additionally needs the iOS app to add **`GoogleSignIn-iOS` 9.0.0 via SPM**.

The web fallback on iOS opens the **external Safari app**
(`UIApplication.sharedApplication.openURL`) — not `SFSafariViewController`, not
`ASWebAuthenticationSession`. No Apple-target artifact links `SafariServices` or `WebKit`.

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
