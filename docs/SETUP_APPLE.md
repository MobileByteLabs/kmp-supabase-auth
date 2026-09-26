---
title: Sign in with Apple Setup
---

# Sign in with Apple Setup

Apple's setup is longer than Google's and has one genuinely dangerous property: **the client
secret expires**. Read §5 before you ship.

> **Read this first — macOS has no native path.** `compose-auth` publishes no macOS artifact at
> all, so `auth-compose` does not reach macOS. macOS consumers use `auth-core` plus the web-OAuth
> fallback. This is measured, not an oversight — see [TARGET_MATRIX.md](../TARGET_MATRIX.md).
>
> **You need the Services ID even for a mobile-only app.** Desktop, web and macOS all take the
> OAuth-redirect path, and that path needs a Services ID. Skipping §2 because "we only ship iOS"
> leaves every non-iOS target unable to sign in with Apple.

If your app offers Google sign-in, App Store Review Guideline 4.8 generally requires an equivalent
privacy-preserving option, and Sign in with Apple is the usual answer. Budget for this rather than
discovering it at submission.

---

## 1. App ID with the capability

[Apple Developer](https://developer.apple.com/account) → **Certificates, Identifiers & Profiles →
Identifiers**.

1. Select your app's App ID (or create one).
2. Enable **Sign In with Apple**. Configure it as a *primary* App ID.
3. Save.

Xcode: add the **Sign in with Apple** capability to the target. This writes the
`com.apple.developer.applesignin` entitlement — without it, the native flow fails at runtime.

## 2. Services ID — the web/redirect identity

**Identifiers → + → Services IDs.**

1. Description and identifier, e.g. `com.yourcompany.yourapp.signin`. It must **differ** from the
   App ID.
2. Enable **Sign In with Apple** → **Configure**:
   - Primary App ID: the App ID from §1.
   - Domains: `<your-project-ref>.supabase.co`
   - Return URLs: `https://<your-project-ref>.supabase.co/auth/v1/callback`
3. Save.

This identifier is `appleServiceId` in `SupabaseAuthConfig`, and the **Client ID** in Supabase's
Apple provider config.

## 3. Sign in with Apple key

**Keys → + →** enable **Sign In with Apple** → Configure → select your primary App ID → Register.

Download the `.p8`. **It downloads exactly once** — Apple will not re-issue it. Store it in a
secret manager immediately; if you lose it, revoke and start over.

Note the **Key ID** and your **Team ID** (top right of the developer portal).

## 4. Generate the client secret

Apple does not issue a client secret string. You generate a **JWT** signed with the `.p8`:

| Claim | Value |
|---|---|
| `iss` | Team ID |
| `iat` | now |
| `exp` | now + up to **6 months** (Apple's hard maximum) |
| `aud` | `https://appleid.apple.com` |
| `sub` | Services ID (§2) |

Header: `alg: ES256`, `kid: <Key ID>`.

Any standard JWT library will do it; Supabase's docs carry a worked example.

## 5. ⚠️ The secret expires — diarise the renewal

**Apple caps the client secret at six months.** When it expires, Sign in with Apple stops working
for every user, with no warning and no deploy to correlate it with. This is a recurring production
outage across the industry, and it is entirely avoidable.

**Do all three now, not later:**

1. Put a calendar reminder **two weeks before** the `exp` you just set.
2. Record the expiry date in your runbook or ops doc.
3. If you have alerting, add a check on Apple sign-in success rate.

Renewal is: generate a new JWT from the same `.p8`, paste it into Supabase. No Apple-side change
needed — which is why it is so easy to forget until it breaks.

## 6. Supabase dashboard

**Authentication → Providers → Apple:**

- Enable.
- **Client ID**: the Services ID (§2).
- **Secret Key**: the JWT (§4).
- Save.

**Authentication → URL Configuration → Redirect URLs**: add `myapp://login-callback`, matching
`SupabaseAuthConfig.redirectUrl` exactly.

## 7. Per-platform callback wiring

### iOS

`cmp-ios/iosApp/Info.plist` (or your equivalent) must register the callback scheme:

```xml
<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLSchemes</key>
    <array>
      <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
    </array>
  </dict>
</array>
```

GoTrue's `ASWebAuthenticationSession` uses this to return to the app. The scheme must match the
scheme half of `redirectUrl`.

### Android

**`auth-core` ships the intent-filter for you** in its `androidMain` manifest; it merges into your
app automatically. You do not add anything.

This matters because it is a real bug the library exists to fix. A production app in this
workspace shipped the Apple button enabled on Android, with the OAuth redirect opening a browser —
and **no intent-filter anywhere**, so there was no way back into the app. The flow was
unfinishable and nothing in the build said so.

If you override the manifest entry, keep scheme and host aligned with `redirectUrl`.

### Desktop / web

Handled by supabase-kt's own system-browser flow. Nothing app-specific beyond the Supabase
redirect allowlist.

## 8. Verify

- [ ] App ID has Sign In with Apple, entitlement present in Xcode
- [ ] Services ID created, domain + return URL point at your Supabase project
- [ ] `.p8` downloaded and stored in a secret manager
- [ ] Client-secret JWT generated, **expiry diarised**
- [ ] Supabase Apple provider has Services ID + JWT
- [ ] Redirect URL on the Supabase allowlist, identical to `redirectUrl`
- [ ] iOS `CFBundleURLSchemes` registered
- [ ] Sample app reaches a signed-in state on a **physical iPhone** (the Simulator does not
      exercise the real ASAuthorization path end to end)

## Troubleshooting

| Symptom | Cause |
|---|---|
| Worked for months, now fails for everyone | **Client secret expired** — see §5 |
| `invalid_client` | Services ID mismatch, or JWT `sub` is not the Services ID |
| Browser opens on Android and never returns | Intent-filter missing — should not happen with `auth-core`, check manifest merge |
| Native sheet never appears on iOS | Entitlement missing, or capability not enabled on the App ID |
| Nothing available on macOS | Expected — `compose-auth` ships no macOS artifact; use the web-OAuth fallback |

## Related

- [SETUP_GOOGLE.md](SETUP_GOOGLE.md)
- [auth-compose README](../auth-compose/README.md)
- [TARGET_MATRIX.md](../TARGET_MATRIX.md)
