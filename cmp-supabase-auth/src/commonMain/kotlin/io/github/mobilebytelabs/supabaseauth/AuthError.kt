package io.github.mobilebytelabs.supabaseauth

/**
 * Why a sign-in attempt did not produce a session.
 *
 * Maps ComposeAuth's `NativeSignInResult` onto a provider-neutral taxonomy, so a feature module
 * never imports a Supabase type to render an error.
 */
public sealed class KmpSupabaseAuthError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /**
     * The user dismissed the provider sheet.
     *
     * NOT a failure. UI must not render this as an error — a person closing a dialog has not hit
     * a problem, and showing them a red banner for it is the most common way these flows feel
     * broken when they are working correctly.
     */
    public data object Cancelled : KmpSupabaseAuthError("Sign-in cancelled by user")

    /** Transport failed. Retryable. */
    public class Network(cause: Throwable? = null) : KmpSupabaseAuthError("Network error during sign-in", cause)

    /** The provider or GoTrue rejected the credential. */
    public class ProviderRejected(public val provider: KmpSupabaseAuthProvider, cause: Throwable? = null) :
        KmpSupabaseAuthError("Provider $provider rejected the sign-in", cause)

    /** Supabase credentials are placeholders, or the provider was never configured. */
    public data object NotConfigured : KmpSupabaseAuthError("Supabase auth is not configured")

    /**
     * The provider was launched and never answered — no success, no error, no cancellation.
     *
     * Distinct from [Unknown], which means something failed and we could not classify it. This
     * means nothing came back AT ALL, which points at configuration rather than a runtime fault:
     * most often the build's signing certificate is not registered against the OAuth client, so
     * Credential Manager finds no usable credential and returns silently.
     */
    public data object NoResponse : KmpSupabaseAuthError("The sign-in provider did not respond")

    /** Anything else. */
    public class Unknown(cause: Throwable? = null) : KmpSupabaseAuthError("Unknown sign-in error", cause)
}
