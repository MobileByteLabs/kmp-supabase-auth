package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient

/** Arms [SupabaseAuthCallbackActivity], which the library's merged manifest registers. */
internal actual fun registerAuthCallbackClient(client: SupabaseClient) {
    SupabaseAuthCallbackActivity.client = client
}
