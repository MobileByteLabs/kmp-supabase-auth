package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google

/**
 * supabase-kt's own web-OAuth flow.
 *
 * On **Android** this is already in-app: [preferInAppBrowser] sets
 * `ExternalAuthAction.CustomTabs`, so `signInWith` launches a Chrome Custom Tab inside the app's
 * task rather than the browser app. Verified on-device 2026-10-02 — the resumed activity is
 * `customtabs.CustomTabActivity`, where it was `ChromeTabbedActivity` before.
 *
 * On desktop / JS / Wasm the system browser is the only surface there is.
 */
internal actual suspend fun SupabaseClient.launchWebOAuth(
    provider: KmpSupabaseAuthProvider,
    redirectUrl: String?,
): Result<Unit> = runCatching {
    when (provider) {
        KmpSupabaseAuthProvider.GOOGLE -> auth.signInWith(Google, redirectUrl = redirectUrl)
        KmpSupabaseAuthProvider.APPLE -> auth.signInWith(Apple, redirectUrl = redirectUrl)
        else -> throw KmpSupabaseAuthError.ProviderRejected(provider)
    }
}
