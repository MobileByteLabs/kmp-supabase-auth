# Architecture

Four decisions shape this library. Each exists because of a specific failure.

## 1. It never creates a Supabase client

The library installs `Auth` and `ComposeAuth` onto the client your app already has, through the
`SupabaseExtrasProvider` seam.

Build a second client to get `Auth` and your generated API bindings hold a *different* instance
carrying no session — so every RLS-gated call resolves no `auth.uid()`. It compiles cleanly,
passes static checks, and fails only at runtime against real policies. Removing the opportunity
is more reliable than documenting the hazard.

## 2. Signed-in comes from the session, never the callback

On Android the native-Google `onResult(Success)` callback frequently never fires even though the
id_token exchange succeeded and the session landed. This was observed on-device: an app sat on
"Signing in…" while Supabase logged `Authenticated`.

So `SupabaseAuthViewModel` derives `isSignedIn` from `AuthRepository.isSignedIn`, and uses
`onResult` **only** for `Error` / `NetworkError` / `ClosedByUser`, which do fire reliably.

`SupabaseAuthViewModelTest.reachesSignedInFromTheSessionAloneWithNoSuccessCallback` pins this: it
reaches signed-in with no callback invoked at all. Rewire the ViewModel to trust the callback and
that test fails.

## 3. The library owns the session; your app owns the user

| Owns | Who |
|---|---|
| Supabase session — tokens, provider identity, status | **library** (`AuthSessionStore`) |
| User profile data, app preferences | **your app**, untouched |
| Attaching the JWT to API calls | **your app's** existing `AuthHeaderBridge` |
| Logout fan-out | **your app's** `UserLogoutManager` / `StoreRegistry` |

`AuthUser` carries identity only — id, email, display name, avatar, provider, anonymous flag. The
library never writes to your preferences store and defines no profile type, so there is never a
second owner of state you already own.

### Why the session store is a plain StateFlow

An earlier design backed it with Store5. That was wrong, for three reasons:

- **Memory only.** GoTrue already persists and refreshes. Caching again would show a signed-in
  user after the real token had expired.
- **Not in the logout purge.** This store is what *tells* the app a logout happened; purging it
  would clear the stream the app reads to notice the purge.
- **Push, not fetch.** GoTrue emits session changes. There is no fetcher to write, and inventing
  one creates a second read path for one piece of state.

Dropping Store5 also lifted the headless module from 8 targets to 17.

## 4. The public API is entirely commonMain

No consumer writes a platform-conditional import to make sign-in work.

Android needs a real OAuth-redirect receiver, so the library ships `SupabaseAuthCallbackActivity`
and registers it by manifest merge — but the class is `internal`, armed from commonMain through
an `internal expect fun registerAuthCallbackClient`. Every non-Android target shares a single
no-op actual.

This matters beyond tidiness. An app in this workspace shipped the Apple button enabled on
Android, where there is no native Apple provider, so the flow fell back to a browser redirect —
with no intent-filter anywhere to return to. The flow was unfinishable and the build was silent.

## Module split

```
cmp-supabase-auth           17 targets   config · client · session store · repository · Koin DI
cmp-supabase-auth-compose    6 targets   remember* wrappers · buttons · login screen · ViewModel
sample-app                        —      runnable proof, not published
```

The split is forced, not stylistic: the Compose compiler plugin applies to every compilation in a
module and fails on any target lacking the runtime, so a Compose-bearing module can never match
its headless sibling's matrix. Keeping `compose-auth` out of the headless module is what buys the
17.

## What comes from upstream

This library does **not** reimplement native sign-in. It builds on:

- [`ComposeAuth`](https://github.com/supabase-community/supabase-kt-plugins/blob/main/ComposeAuth/README.md)
  — `rememberSignInWithGoogle` / `rememberSignInWithApple`, `NativeSignInResult`,
  `LINK_IDENTITY_CALLBACK`
- [`ComposeAuthUI`](https://github.com/supabase-community/supabase-kt-plugins/blob/main/ComposeAuthUI/README.md)
  — `ProviderButtonContent`, form fields, validators (flagged experimental upstream; the opt-in is
  contained to one file here)

What this library adds is the part those do not cover: DI, a provider-neutral domain surface, the
session-truth ViewModel, the token bridge, and the Android callback receiver.
