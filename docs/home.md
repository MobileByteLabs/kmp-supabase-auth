# KMP Supabase Auth

**Supabase authentication for Kotlin Multiplatform** — native Google sign-in through the Android
Credential Manager, native Apple sign-in through `ASAuthorization` on iOS, anonymous sessions with
an id-preserving upgrade, and a web-OAuth fallback everywhere else.

The public API is **entirely commonMain**. No consumer writes a platform-conditional import to
make sign-in work.

<!-- docs-gen:install:begin -->
```kotlin
dependencies {
    implementation("io.github.mobilebytelabs:cmp-supabase-auth:0.1.1")  // headless
    implementation("io.github.mobilebytelabs:cmp-supabase-auth-compose:0.1.1")  // Compose UI (optional)
}
```
<!-- docs-gen:install:end -->

```kotlin
val AppModule = module {
    includes(
        supabaseAuth(SupabaseAuthConfig(projectRef = "…", googleWebClientId = "…")),
        supabaseAuthComposeModule(),
    )
}

SupabaseLoginScreen(
    viewModel = koinViewModel(),
    client = koinInject(),
    onSignedIn = { navigateHome() },
)
```

[Install &amp; wire up →](getting-started.md)

---

## Why this exists

Four apps in one workspace had each built their own Supabase sign-in. **One of them worked.** The
others were a provider interface wired to nothing, a REST client with the native token hardcoded
to `""`, and a Supabase client with no auth at all. A fifth shipped an Apple button on Android
that opened a browser it could never return from.

Auth is the worst thing to re-implement per app: a bug in it is a security bug, and a fix that
only reaches the *next* project is close to worthless.

## What it gives you

| | |
|---|---|
| **Native sign-in** | Credential Manager (Android) · ASAuthorization (iOS) · OAuth redirect elsewhere — the plugin picks per platform |
| **Guest sessions** | `signInAnonymously()` with RLS from the first write; `linkIdentity` upgrades **without changing the user id** |
| **Layered DI** | One `includes(supabaseAuth(config))`, or one line per rung across `core/network` → `core/store` → `core/data` |
| **Drop-in UI** | Slot-based `SupabaseLoginScreen`, brand-compliant provider buttons, a session-driven ViewModel |
| **Testability** | `FakeAuthRepository` ships in the main artifact |

## Three things worth knowing before you build on it

**It never creates a Supabase client.** It installs `Auth` and `ComposeAuth` onto the one your app
already has. A second client carries no session, so every RLS-gated call resolves no `auth.uid()`
— while compiling cleanly and passing static checks the whole way.

**Signed-in comes from the session, never the button callback.** On Android the native Google
`onResult(Success)` callback frequently never fires even though the session landed. Observed
on-device: the app sat on "Signing in…" while Supabase logged `Authenticated`.

**The library owns the session; your app keeps owning the user.** Profile data stays in your
store, untouched. `AuthUser` carries identity only.

[Read the architecture →](architecture.md)

## Platform support

|  | Android | iOS | Desktop | Web | macOS |
|---|---|---|---|---|---|
| Google | **native** | **native** | redirect | redirect | redirect |
| Apple | redirect | **native** | redirect | redirect | redirect |
| Anonymous | ✅ | ✅ | ✅ | ✅ | ✅ |

macOS gets no native Apple sign-in — `compose-auth` publishes no macOS artifact. Measured, not an
oversight; see the [target matrix](../TARGET_MATRIX.md).

## Built on

- [ComposeAuth](https://github.com/supabase-community/supabase-kt-plugins/blob/main/ComposeAuth/README.md) — native sign-in composables
- [ComposeAuthUI](https://github.com/supabase-community/supabase-kt-plugins/blob/main/ComposeAuthUI/README.md) — provider buttons, form fields, validators
- [supabase-kt](https://github.com/supabase-community/supabase-kt) — the GoTrue client

This library adds what those do not: DI, a provider-neutral domain surface, the session-truth
ViewModel, the token bridge, and the Android OAuth callback receiver.
