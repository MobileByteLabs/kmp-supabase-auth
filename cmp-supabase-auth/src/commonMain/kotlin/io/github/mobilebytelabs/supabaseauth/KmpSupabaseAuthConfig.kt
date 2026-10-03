package io.github.mobilebytelabs.supabaseauth

/**
 * Everything the library needs to install Supabase auth onto a consumer's existing client.
 *
 * [projectRef] is the Supabase project ref — the same id the consumer declared for its access
 * point. It is how the library finds the ONE client to install onto; the library never creates a
 * client of its own. Building a second client to get `Auth` is the defect this design exists to
 * prevent: the app's generated API bindings would then hold a *different* instance carrying no
 * session, so every RLS-gated call resolves no `auth.uid()` — while compiling cleanly throughout.
 *
 * Every provider field is optional and blank-tolerant on purpose. An app part-way through console
 * setup must still build and run, degrading to the OAuth-redirect path rather than throwing.
 * "Unconfigured" is a supported state, not an error.
 */
public data class KmpSupabaseAuthConfig(
    val projectRef: String,
    /**
     * Google Cloud **Web** client id — not the Android one and not the iOS one.
     *
     * Both Supabase GoTrue and the Android Credential Manager want the *Web* client id. Supplying
     * the Android client id here is the single most common setup mistake, and it fails at runtime
     * with an opaque provider error rather than at build time. See `docs/SETUP_GOOGLE.md`.
     */
    val googleWebClientId: String = "",
    /** Apple Services ID, for the web/redirect Apple path. See `docs/SETUP_APPLE.md`. */
    val appleServiceId: String = "",
    /** App callback URL as `scheme://host`, e.g. `myapp://login-callback`. */
    val redirectUrl: String = "",
) {
    /**
     * True when the native Google flow can be installed: the platform can deliver it
     * ([googleNativeSupported]) AND it is configured.
     *
     * False degrades to an in-app web flow, which is a working path — whereas half-configuring
     * the native flow is not. Blank and whitespace both count as absent.
     *
     * **Always false on iOS**, by delivery rather than by configuration — the native iOS path needs
     * the GoogleSignIn SDK, which no Maven-published library can put into an app's link graph. See
     * [googleNativeSupported]. There is deliberately no iOS client id field to set: it would read
     * as "configure this and native iOS works", which is not true.
     */
    public val hasGoogleNative: Boolean
        get() = googleNativeSupported && googleWebClientId.isNotBlank()

    /** Scheme half of [redirectUrl], or null when absent or malformed. */
    public val oauthScheme: String? get() = redirectUrl.substringBeforeOrNull("://")

    /** Host half of [redirectUrl], or null when absent or malformed. */
    public val oauthHost: String? get() = redirectUrl.substringAfterOrNull("://")

    /**
     * Whether real (non-placeholder) credentials are present.
     *
     * Checks for placeholder SHAPE, not merely emptiness: a config left at `YOUR_PROJECT_REF`
     * is misconfigured in exactly the way a blank one is, and catching only the blank case lets
     * an unfilled template reach a release build.
     */
    public val isConfigured: Boolean
        get() = projectRef.isNotBlank() && !projectRef.contains("YOUR_", ignoreCase = true)
}

private fun String.substringBeforeOrNull(delimiter: String): String? =
    if (contains(delimiter)) substringBefore(delimiter).ifBlank { null } else null

private fun String.substringAfterOrNull(delimiter: String): String? =
    if (contains(delimiter)) substringAfter(delimiter).ifBlank { null } else null
