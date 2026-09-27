package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient

/**
 * No-op.
 *
 * iOS/macOS return through `ASWebAuthenticationSession`, desktop and web through the system
 * browser — supabase-kt handles each itself, so there is nothing to arm. Declared rather than
 * omitted so the commonMain API stays whole on every target.
 */
internal actual fun registerAuthCallbackClient(client: SupabaseClient): Unit = Unit
