package io.github.mobilebytelabs.supabaseauth.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.SupabaseClientBuilder
import io.github.mobilebytelabs.supabaseauth.DefaultKmpSupabaseAuthRepository
import io.github.mobilebytelabs.supabaseauth.DefaultKmpSupabaseAuthSessionStore
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthClient
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthConfig
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthOptions
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthRepository
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthSessionStore
import io.github.mobilebytelabs.supabaseauth.buildOptions
import io.github.mobilebytelabs.supabaseauth.internal.KmpSupabaseAuthClientImpl
import io.github.mobilebytelabs.supabaseauth.internal.buildAuthExtras
import io.github.mobilebytelabs.supabaseauth.registerAuthCallbackClient
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
 *             "your-project-ref" -> kmpSupabaseAuthExtras(config)
 *             else -> { {} }
 *         }
 *     }
 * }
 * ```
 *
 * For native Google/Apple, compose it with `kmpSupabaseComposeAuthExtras(...)` from
 * `cmp-supabase-auth-compose`.
 */
public fun kmpSupabaseAuthExtras(
    config: KmpSupabaseAuthConfig,
    configure: KmpSupabaseAuthOptions.() -> Unit = {},
): SupabaseClientBuilder.() -> Unit = buildAuthExtras(config, buildOptions(configure))

/**
 * Rung 1 — `core/network`. Binds [KmpSupabaseAuthClient] over the app's existing [SupabaseClient].
 *
 * [clientProvider] defaults to `get()`, which suits an app binding `SupabaseClient` directly.
 * kmp-project-template does NOT: it binds `SupabaseClientFactory` and reaches the client through
 * `requireClientFor(id)`. Such a consumer passes the lookup explicitly:
 *
 * ```
 * includes(
 *     kmpSupabaseAuthNetwork(config) {
 *         get<SupabaseClientFactory>().requireClientFor(config.projectRef).client
 *     },
 * )
 * ```
 *
 * This module NEVER builds a client — a second one carries no session, so every RLS-gated call
 * resolves no `auth.uid()` while compiling cleanly.
 */
public fun kmpSupabaseAuthNetwork(
    config: KmpSupabaseAuthConfig,
    configure: KmpSupabaseAuthOptions.() -> Unit = {},
    clientProvider: Scope.() -> SupabaseClient = { get() },
): Module = module {
    single { buildOptions(configure) }
    single<KmpSupabaseAuthClient> {
        val supabase = clientProvider(this)
        // Arm the platform OAuth-redirect receiver before anything can navigate away. On
        // Android that is the library's own callback activity; elsewhere it is a no-op.
        registerAuthCallbackClient(supabase)
        KmpSupabaseAuthClientImpl(
            client = supabase,
            options = get(),
            isConfigured = config.isConfigured,
        )
    }
}

/** Rung 2 — `core/store`. */
public fun kmpSupabaseAuthStore(): Module = module {
    single<KmpSupabaseAuthSessionStore> { DefaultKmpSupabaseAuthSessionStore(client = get()) }
}

/** Rung 3 — `core/data`. */
public fun kmpSupabaseAuthRepository(): Module = module {
    single<KmpSupabaseAuthRepository> { DefaultKmpSupabaseAuthRepository(client = get(), store = get()) }
}

/**
 * All three rungs at once, for an app that does not want per-layer placement.
 *
 * Defined AS the three rung modules rather than duplicating their bindings — that is what makes
 * "one line" and "three lines" provably identical. `ModuleParityTest` asserts it.
 */
public fun kmpSupabaseAuth(
    config: KmpSupabaseAuthConfig,
    configure: KmpSupabaseAuthOptions.() -> Unit = {},
    clientProvider: Scope.() -> SupabaseClient = { get() },
): Module = module {
    includes(
        kmpSupabaseAuthNetwork(config, configure, clientProvider),
        kmpSupabaseAuthStore(),
        kmpSupabaseAuthRepository(),
    )
}
