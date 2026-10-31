package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient
import io.github.mobilebytelabs.supabaseauth.internal.KmpSupabaseAuthClientImpl

/**
 * A [KmpSupabaseAuthClient] over a Supabase client you already own — WITHOUT registering anything in
 * Koin.
 *
 * For an SDK that signs users into its OWN Supabase project inside an app that may use this library
 * for its own sign-in (PayCraft is the first). The app's [KmpSupabaseAuthClient] lives in the app's
 * Koin graph; a second binding there would collide or replace it. Build the SDK's client with
 * `kmpSupabaseAuthInstall(config)` applied, wrap it here, and pass the result EXPLICITLY to the
 * launchers (`rememberKmpSupabaseAppleSignIn(client = …)`).
 *
 * Give [config] a redirect with a distinct PATH — `<applicationId>://login-callback/<sdk>` — so the
 * Android callback hands this client its own redirects and the app keeps `login-callback`.
 */
public fun kmpSupabaseAuthClientFor(
    client: SupabaseClient,
    config: KmpSupabaseAuthConfig,
    configure: KmpSupabaseAuthOptions.() -> Unit = {},
): KmpSupabaseAuthClient {
    registerAuthCallbackClient(client, config.redirectUrl)
    return KmpSupabaseAuthClientImpl(
        client = client,
        options = buildOptions(configure),
        isConfigured = config.isConfigured,
        redirectUrl = config.redirectUrl,
    )
}
