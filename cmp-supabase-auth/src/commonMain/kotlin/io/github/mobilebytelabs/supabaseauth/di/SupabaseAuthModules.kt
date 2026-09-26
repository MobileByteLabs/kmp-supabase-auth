package io.github.mobilebytelabs.supabaseauth.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.SupabaseClientBuilder
import io.github.mobilebytelabs.supabaseauth.AuthRepository
import io.github.mobilebytelabs.supabaseauth.AuthSessionStore
import io.github.mobilebytelabs.supabaseauth.DefaultAuthRepository
import io.github.mobilebytelabs.supabaseauth.DefaultAuthSessionStore
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthClient
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthConfig
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthOptions
import io.github.mobilebytelabs.supabaseauth.buildOptions
import io.github.mobilebytelabs.supabaseauth.internal.SupabaseAuthClientImpl
import io.github.mobilebytelabs.supabaseauth.internal.buildAuthExtras
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * The `SupabaseClientBuilder` block for the consumer's client — installs `Auth`.
 *
 * Your `core/network` is codegen'd from the Supabase training layer, so it already builds the
 * client; this only supplies the extras block:
 *
 * ```
 * single<SupabaseExtrasProvider> {
 *     SupabaseExtrasProvider { id ->
 *         when (id) {
 *             "your-project-ref" -> supabaseAuthExtras(config)
 *             else -> { {} }
 *         }
 *     }
 * }
 * ```
 *
 * For native Google/Apple, compose it with `supabaseComposeAuthExtras(...)` from
 * `cmp-supabase-auth-compose`.
 */
public fun supabaseAuthExtras(
    config: SupabaseAuthConfig,
    configure: SupabaseAuthOptions.() -> Unit = {},
): SupabaseClientBuilder.() -> Unit = buildAuthExtras(config, buildOptions(configure))

/**
 * Rung 1 — `core/network`. Binds [SupabaseAuthClient] over the app's existing [SupabaseClient].
 *
 * [clientProvider] defaults to `get()`, which suits an app binding `SupabaseClient` directly.
 * kmp-project-template does NOT: it binds `SupabaseClientFactory` and reaches the client through
 * `requireClientFor(id)`. Such a consumer passes the lookup explicitly:
 *
 * ```
 * includes(
 *     supabaseAuthNetwork(config) {
 *         get<SupabaseClientFactory>().requireClientFor(config.projectRef).client
 *     },
 * )
 * ```
 *
 * This module NEVER builds a client — a second one carries no session, so every RLS-gated call
 * resolves no `auth.uid()` while compiling cleanly.
 */
public fun supabaseAuthNetwork(
    config: SupabaseAuthConfig,
    configure: SupabaseAuthOptions.() -> Unit = {},
    clientProvider: Scope.() -> SupabaseClient = { get() },
): Module = module {
    single { buildOptions(configure) }
    single<SupabaseAuthClient> {
        SupabaseAuthClientImpl(
            client = clientProvider(this),
            options = get(),
            isConfigured = config.isConfigured,
        )
    }
}

/** Rung 2 — `core/store`. */
public fun supabaseAuthStore(): Module = module {
    single<AuthSessionStore> { DefaultAuthSessionStore(client = get()) }
}

/** Rung 3 — `core/data`. */
public fun supabaseAuthRepository(): Module = module {
    single<AuthRepository> { DefaultAuthRepository(client = get(), store = get()) }
}

/**
 * All three rungs at once, for an app that does not want per-layer placement.
 *
 * Defined AS the three rung modules rather than duplicating their bindings — that is what makes
 * "one line" and "three lines" provably identical. `ModuleParityTest` asserts it.
 */
public fun supabaseAuth(
    config: SupabaseAuthConfig,
    configure: SupabaseAuthOptions.() -> Unit = {},
    clientProvider: Scope.() -> SupabaseClient = { get() },
): Module = module {
    includes(
        supabaseAuthNetwork(config, configure, clientProvider),
        supabaseAuthStore(),
        supabaseAuthRepository(),
    )
}
