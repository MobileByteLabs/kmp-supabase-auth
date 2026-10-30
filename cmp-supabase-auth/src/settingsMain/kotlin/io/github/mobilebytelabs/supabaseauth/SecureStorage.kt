package io.github.mobilebytelabs.supabaseauth

import com.russhwolf.settings.Settings
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager

/**
 * Keep the signed-in session — access AND refresh token — and the PKCE code verifier in [settings]
 * instead of supabase-kt's default store.
 *
 * The default is PLAIN storage: SharedPreferences on Android, NSUserDefaults on Apple, a file on
 * desktop. A refresh token is a long-lived credential — whoever copies it (a backup, a rooted device,
 * a debug dump) is that user until it is revoked. Pass the app's ENCRYPTED settings (Android Keystore /
 * iOS Keychain backed); in kmp-project-template that is `get<Settings>(named("secure"))`:
 *
 * ```kotlin
 * kmpSupabaseAuthInstall(config) { secureStorage(get(named("secure"))) }
 * ```
 *
 * Opt-in because the library cannot create platform-secure storage itself, and the store must be the
 * app's — the same instance across launches, or every launch starts signed out.
 *
 * Available where supabase-kt ships its Settings-backed session store (Android, Apple, JVM, JS,
 * Wasm/JS). Linux and Windows have none to plug into, so the option does not exist there rather than
 * silently doing nothing.
 */
public fun KmpSupabaseAuthOptions.secureStorage(settings: Settings) {
    authConfig = {
        sessionManager = SettingsSessionManager(settings)
        codeVerifierCache = SettingsCodeVerifierCache(settings)
    }
}
