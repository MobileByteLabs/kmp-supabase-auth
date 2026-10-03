package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.auth.AuthConfig

/**
 * Routes the OAuth-redirect fallback through an IN-APP browser where the platform has one.
 *
 * **Why this exists.** supabase-kt's default is `ExternalAuthAction.ExternalBrowser` — verified by
 * decompiling auth-kt 3.8.0, whose `ExternalAuthAction.Companion.DEFAULT` returns
 * `ExternalBrowser.INSTANCE`. Nothing was overriding it, so every web fallback threw the user out
 * of the app into the full browser. OBSERVED on a device 2026-10-02: tapping "Continue with Apple"
 * on Android resumed `com.android.chrome/…ChromeTabbedActivity` — the whole Chrome app, not a tab.
 *
 * That is worse than cosmetic. Leaving the app loses the task stack on some launchers, the user
 * sees Chrome's UI and URL bar rather than the app's, and returning depends entirely on the
 * callback scheme surviving a cold app. An in-app tab keeps the flow inside the app's own task.
 *
 * `internal expect` matches [registerAuthCallbackClient]: the public API of this library stays
 * entirely commonMain, so no consumer writes a platform-conditional import to get this.
 *
 * **Platform reality, measured — not assumed:**
 * - **Android** → `ExternalAuthAction.CustomTabs`. `androidx.browser` is ALREADY a transitive
 *   dependency of `auth-kt-android`, so this costs no new dependency.
 * - **iOS/macOS** → NO-OP, because supabase-kt cannot do it. The shipped iOS klib declares no
 *   `ExternalAuthAction` at all, and no `ASWebAuthenticationSession`, `SFSafariViewController`,
 *   `SafariServices` or `AuthenticationServices` symbol; it opens the URL with
 *   `UIApplication.openURL`, which is the external Safari app by definition. Getting an in-app
 *   sheet on iOS needs an upstream change to supabase-kt (or reimplementing its URL-opening seam),
 *   NOT a flag here — so this function deliberately does nothing rather than pretending.
 *   In practice iOS is least affected: Apple sign-in is native there, and Google is native too
 *   when an iOS client id is configured, so the web fallback is the exception rather than the rule.
 * - **desktop / JS / Wasm** → NO-OP; the system browser is the only sensible surface.
 */
internal expect fun AuthConfig.preferInAppBrowser()
