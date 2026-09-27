package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient

/**
 * Hands the client to the platform's OAuth-redirect callback receiver, where one exists.
 *
 * `internal expect` on purpose: the public API of this library is **entirely commonMain**, so no
 * consumer ever writes a platform-conditional import to make sign-in work. Android needs a real
 * receiver (see the `androidMain` actual); every other target returns to the app through
 * supabase-kt's own mechanism and the actual is a no-op.
 *
 * Called once from the DI graph, so the receiver is armed before any redirect can land.
 */
internal expect fun registerAuthCallbackClient(client: SupabaseClient)
