package io.github.mobilebytelabs.supabaseauth.internal

import io.github.jan.supabase.SupabaseClientBuilder
import io.github.jan.supabase.auth.Auth
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthConfig
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthOptions

/**
 * The block handed to the consumer's Supabase client builder. Installs Auth + ComposeAuth onto
 * the app's EXISTING client.
 *
 * This is why the library never calls `createSupabaseClient`: a second client would carry no
 * session, so every generated API binding would resolve no `auth.uid()` on RLS-gated calls —
 * compiling cleanly the whole way.
 *
 * Installs `Auth` ONLY. The ComposeAuth plugin is installed by `cmp-supabase-auth-compose`'s
 * `kmpSupabaseComposeAuthExtras(...)`, because `compose-auth` is a Compose artifact and depending on
 * it here would cut this module from 17 targets to 7. A consumer using the Compose module
 * composes both blocks; a headless consumer installs just this one and gets OAuth-redirect
 * sign-in on every platform.
 */
internal fun buildAuthExtras(
    config: KmpSupabaseAuthConfig,
    options: KmpSupabaseAuthOptions,
): SupabaseClientBuilder.() -> Unit = {
    install(Auth) {
        // The OAuth-redirect fallback (iOS/macOS the EXTERNAL Safari app, desktop system
        // browser, JS/Wasm) needs the app's callback scheme + host so GoTrue can intercept the
        // return URL. Harmless where the native path is taken instead.
        //
        // On iOS supabase-kt launches this with `UIApplication.sharedApplication.openURL(...)`
        // (Auth/src/iosMain/.../openUrl.kt), NOT ASWebAuthenticationSession and NOT
        // SFSafariViewController — verified against supabase-kt 3.8.0, whose shipped iOS klib
        // declares no SafariServices and no WebKit dependency. The user leaves the app for
        // Safari and returns through the scheme below.
        config.oauthScheme?.let { scheme = it }
        config.oauthHost?.let { host = it }
    }
    options.extraInstall(this)
}
