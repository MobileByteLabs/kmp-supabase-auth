package io.github.mobilebytelabs.supabaseauth.compose

import io.github.jan.supabase.SupabaseClientBuilder
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthConfig
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthOptions
import io.github.mobilebytelabs.supabaseauth.di.supabaseAuthExtras

/**
 * Everything this library installs into a Supabase client, as ONE branch of a fork's
 * `SupabaseExtrasProvider`.
 *
 * ```kotlin
 * single<SupabaseExtrasProvider> {
 *     SupabaseExtrasProvider { id ->
 *         when (id) {
 *             AUTH_ACCESS_POINT -> supabaseAuthInstall(AuthConfig)
 *             ANALYTICS_POINT   -> { { install(Realtime) } }
 *             else              -> { {} }
 *         }
 *     }
 * }
 * ```
 *
 * Replaces the two-call form, which every consumer otherwise had to write — and get in the right
 * order — by hand:
 *
 * ```kotlin
 * {
 *     supabaseAuthExtras(config)(this)
 *     supabaseComposeAuthExtras(config)(this)
 * }
 * ```
 *
 * **It composes into the fork's provider rather than binding one.** The template resolves exactly
 * one `SupabaseExtrasProvider` (`getOrNull<SupabaseExtrasProvider>()?.forId(id)`), and a fork may
 * run several projects — Auth on one, Realtime or Storage on others. A library that bound that
 * single itself would collide with the fork's own binding (Koin raises
 * `DefinitionOverrideException` on a duplicate type) and take the extension point away from the
 * one place that can see all the access points. Returning a builder keeps the `when` the single
 * place auth is wired, without owning it.
 *
 * Pair it with `supabaseAuth(config, clientProvider = …)` in the same module — that registers the
 * network/store/repository rungs. Installing the plugins without the graph gives you an
 * authenticated client no repository reads from; the graph without the plugins gives you a
 * repository with no `Auth` installed.
 *
 * @param googleNative pass `false` to force Google through the web-OAuth fallback.
 * @param appleNative pass `false` to force Apple through the web-OAuth fallback.
 */
public fun supabaseAuthInstall(
    config: SupabaseAuthConfig,
    googleNative: Boolean = true,
    appleNative: Boolean = true,
    configure: SupabaseAuthOptions.() -> Unit = {},
): SupabaseClientBuilder.() -> Unit = {
    supabaseAuthExtras(config, configure)(this)
    supabaseComposeAuthExtras(config, googleNative, appleNative)(this)
}
