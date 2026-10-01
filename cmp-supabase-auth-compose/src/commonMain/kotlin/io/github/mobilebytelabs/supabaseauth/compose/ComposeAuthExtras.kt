package io.github.mobilebytelabs.supabaseauth.compose

import io.github.jan.supabase.SupabaseClientBuilder
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.appleNativeLogin
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthConfig
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthLog

/**
 * Installs the ComposeAuth plugin — the native-sign-in half of the client configuration.
 *
 * Lives HERE rather than in `cmp-supabase-auth` because `compose-auth` publishes only 7 targets;
 * depending on it from the headless module would cut that module from 17 targets to 7.
 *
 * Compose it with the headless block in your codegen'd `core/network`:
 *
 * ```
 * single<SupabaseExtrasProvider> {
 *     SupabaseExtrasProvider { id ->
 *         when (id) {
 *             config.projectRef -> {
 *                 {
 *                     kmpSupabaseAuthExtras(config)(this)          // Auth
 *                     kmpSupabaseComposeAuthExtras(config)(this)   // ComposeAuth
 *                 }
 *             }
 *             else -> { {} }
 *         }
 *     }
 * }
 * ```
 *
 * Per the plugin's own documentation, native Google is Android + iOS and native Apple is iOS
 * only; every other target falls back to `auth-kt`'s OAuth flow automatically. Declaring both
 * unconditionally is therefore correct — the plugin selects the path per platform.
 */
public fun kmpSupabaseComposeAuthExtras(
    config: KmpSupabaseAuthConfig,
    googleNative: Boolean = true,
    appleNative: Boolean = true,
): SupabaseClientBuilder.() -> Unit = {
    install(ComposeAuth) {
        // Blank client id => skip the native flow and let the OAuth redirect handle it.
        // Half-configuring native fails at runtime with an opaque provider error; skipping it
        // degrades to a path that actually works.
        // The single most valuable line in this library's logs: it says which path the app will
        // ACTUALLY take, at the moment the decision is made. A blank client id silently demotes
        // Google to the browser fallback, and nothing downstream reports that.
        if (googleNative && config.hasGoogleNative) {
            KmpSupabaseAuthLog.log {
                "install: GOOGLE native (serverClientId=${KmpSupabaseAuthLog.redacted(config.googleWebClientId)})"
            }
            googleNativeLogin(serverClientId = config.googleWebClientId)
        } else {
            KmpSupabaseAuthLog.log {
                "install: GOOGLE native SKIPPED — " +
                    if (!googleNative) {
                        "googleNative=false"
                    } else {
                        "googleWebClientId is ${KmpSupabaseAuthLog.redacted(config.googleWebClientId)}" +
                            " → sign-in will use the web OAuth fallback"
                    }
            }
        }
        if (appleNative) {
            KmpSupabaseAuthLog.log { "install: APPLE native (iOS only; web fallback elsewhere)" }
            appleNativeLogin()
        } else {
            KmpSupabaseAuthLog.log { "install: APPLE native SKIPPED — appleNative=false" }
        }
    }
}
