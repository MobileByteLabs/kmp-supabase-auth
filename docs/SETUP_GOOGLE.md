---
title: Google Sign-In Setup
---

# Google Sign-In Setup

Everything you need in the Google Cloud Console and the Supabase dashboard before native Google
sign-in will work. Follow it top to bottom — several steps depend on values produced earlier.

> **The one mistake everyone makes:** the library wants the **Web** client id, not the Android or
> iOS one. Both Supabase GoTrue and the Android Credential Manager verify against the Web client
> id. Supplying the Android id compiles, builds and installs fine, then fails at runtime with an
> opaque provider error. If sign-in fails and nothing looks wrong, check this first.

---

## 1. Google Cloud Console — project and consent screen

1. Open the [Google Cloud Console](https://console.cloud.google.com/) and create (or select) a project.
2. **APIs & Services → OAuth consent screen**:
   - User type: *External* unless you have a Workspace-only audience.
   - App name, support email, developer contact — these are shown to users, so use real values.
   - Scopes: `email`, `profile`, `openid` are enough. Adding more triggers Google verification.
3. While in testing, add your own account under **Test users** or sign-in will be refused.

## 2. Create the OAuth clients

You need **up to three**, and they are not interchangeable.

### 2a. Web client — required, always

**APIs & Services → Credentials → Create credentials → OAuth client ID → Web application.**

- Authorised redirect URI: `https://<your-project-ref>.supabase.co/auth/v1/callback`

Copy the **Client ID** and **Client secret**. This client id is:

- what you pass as `googleWebClientId` in `KmpSupabaseAuthConfig`
- what Android's Credential Manager uses as its `serverClientId`
- what you paste into Supabase's Google provider config

### 2b. Android client — required for native Android sign-in

**Create credentials → OAuth client ID → Android.**

- Package name: your application id.
- SHA-1 certificate fingerprint.

You need **one Android client per signing certificate**, and most apps have at least three:

| Certificate | Where to get the SHA-1 |
|---|---|
| Debug | `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android` |
| Release (upload) | `keytool -list -v -keystore <your-upload>.jks -alias <alias>` |
| Release (app signing) | Play Console → your app → **Setup → App signing → App signing key certificate** |

> **Play App Signing is the classic trap.** If you use it, Google re-signs your app with a
> *different* key than your upload key. Register the **app signing** SHA-1 too, or sign-in works
> for every internal build and fails for everyone who installs from the Play Store.

Also register SHA-256 — some Google services require it, and adding it now costs nothing.

You never reference the Android client id in code. Its existence is what authorises the package
plus certificate pair.

### 2c. iOS client — required for native iOS Google sign-in

**Create credentials → OAuth client ID → iOS.** Bundle id = your app's. Pass the resulting id as
`googleIosClientId`.

## 3. Supabase dashboard

**Authentication → Providers → Google:**

- Enable the provider.
- **Client ID** and **Client Secret**: the values from the **Web** client (2a).
- Save.

**Authentication → URL Configuration → Redirect URLs** — add your app's callback:

```
myapp://login-callback
```

This must match `KmpSupabaseAuthConfig.redirectUrl` exactly. GoTrue refuses any redirect not on this
allowlist, and the resulting error does not name the allowlist as the cause.

## 4. Wire it into the app

Keep the client id out of source control — read it through BuildKonfig or an equivalent:

```kotlin
KmpSupabaseAuthConfig(
    projectRef        = "your-project-ref",
    googleWebClientId = BuildKonfig.GOOGLE_OAUTH_WEB_CLIENT_ID,
    redirectUrl       = "myapp://login-callback",
)
```

Leaving `googleWebClientId` blank is safe: the library skips the native flow and falls back to the
OAuth redirect rather than throwing. That is deliberate, so a half-configured project still runs.

## 5. Android callback

Needed only for the **OAuth-redirect** path (which on Android means Apple sign-in, since Google is
native). `cmp-supabase-auth` ships the intent-filter in its `androidMain` manifest and it merges into your
app automatically — you do not add anything. See [SETUP_APPLE.md](SETUP_APPLE.md).

## 6. Verify

Run the sample app (`sample-app/`) against your project before touching your own app — it isolates
console configuration from app wiring.

- [ ] Web client id set in Supabase's Google provider
- [ ] Android client registered for **every** signing certificate, Play App Signing included
- [ ] iOS client registered, bundle id matches
- [ ] Redirect URL in the Supabase allowlist, identical to `redirectUrl`
- [ ] `googleWebClientId` is the **Web** id
- [ ] Sample app reaches a signed-in state on a physical Android device

## Troubleshooting

| Symptom | Cause |
|---|---|
| Sheet opens, then "something went wrong" | Wrong client id — Android's instead of Web's |
| Works on debug, fails from Play Store | Play App Signing SHA-1 not registered |
| Sign-in succeeds but the app stays on the spinner | UI keyed on the provider callback instead of the session stream — see [cmp-supabase-auth-compose](../cmp-supabase-auth-compose/README.md) |
| `redirect_uri_mismatch` | Callback URL missing from Supabase's allowlist, or differs from `redirectUrl` |
| Nothing happens on tap | `googleWebClientId` blank — the library degraded to OAuth redirect by design |

## Related

- [SETUP_APPLE.md](SETUP_APPLE.md)
- [cmp-supabase-auth README](../cmp-supabase-auth/README.md)
- [TARGET_MATRIX.md](../TARGET_MATRIX.md)
