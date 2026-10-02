package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient

/**
 * Starts the web-OAuth leg for [provider] — IN-APP wherever the platform can.
 *
 * **Why this is an expect at all.** Apple REJECTED this app under Guideline 4 - Design:
 *
 * > "the user is taken to the default web browser to sign in or register for an account, which
 * > provides a poor user experience … You may also choose to implement the Safari View Controller
 * > API to display web content within the app."
 *
 * supabase-kt cannot satisfy that on iOS. MEASURED against 3.8.0 — `auth-kt`, `compose-auth` and
 * `compose-auth-ui` iOS klibs contain ZERO references to `SFSafariViewController`,
 * `ASWebAuthenticationSession`, `SafariServices` or even `ExternalAuthAction`; the iOS path opens
 * the URL with `UIApplication.openURL`, which IS the external Safari app by definition. So this is
 * not a flag that was left unset — the capability does not exist upstream and had to be built here.
 *
 * - **iOS** → `ASWebAuthenticationSession` (Apple's own API, SFSafariViewController-backed, and the
 *   one Apple's reviewers name). See the iosMain actual.
 * - **Android** → supabase-kt's own flow, already routed through Chrome Custom Tabs by
 *   [preferInAppBrowser]; nothing more is needed and nothing is gained by duplicating it.
 * - **everything else** → supabase-kt's flow; a desktop/JS system browser is the only surface.
 */
internal expect suspend fun SupabaseClient.launchWebOAuth(
    provider: KmpSupabaseAuthProvider,
    redirectUrl: String?,
): Result<Unit>
