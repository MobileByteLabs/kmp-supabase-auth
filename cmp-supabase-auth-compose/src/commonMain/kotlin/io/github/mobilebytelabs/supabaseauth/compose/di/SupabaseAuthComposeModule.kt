package io.github.mobilebytelabs.supabaseauth.compose.di

import io.github.mobilebytelabs.supabaseauth.compose.SupabaseAuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Rung 4 — the feature module.
 *
 * A `factory`, not a `single`: each login screen gets its own ViewModel, so a sign-in attempt
 * abandoned on one screen cannot leave stale loading or error state visible on the next.
 */
public fun supabaseAuthComposeModule(): Module = module {
    factory {
        SupabaseAuthViewModel(
            repository = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
}
