package io.github.mobilebytelabs.supabaseauth

/**
 * True where a `myapp://…` redirect reaches the app: Android (the merged callback activity) and iOS
 * (ASWebAuthenticationSession's callback scheme). False on JS/Wasm/desktop, where a browser cannot open it.
 */
internal expect val deliversAppSchemeRedirect: Boolean

/**
 * The redirect to hand the provider. [KmpSupabaseAuthConfig.redirectUrl] is one app-scheme URL for every
 * target, so on a target that cannot receive it the provider would land the user on a dead link. Null there
 * lets supabase-kt pick its own: the current page on JS/Wasm (which it parses back into a session on load),
 * its local callback server on the JVM. An explicit http(s) redirect is honoured everywhere.
 */
internal fun webOAuthRedirect(redirectUrl: String?, deliversAppScheme: Boolean): String? {
    val url = redirectUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return url.takeIf { deliversAppScheme || it.startsWith("https://") || it.startsWith("http://") }
}
