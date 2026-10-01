package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.AuthError

/**
 * Where a sign-in screen is in its lifecycle.
 *
 * A phase rather than a set of booleans because the states are mutually exclusive, and booleans
 * let a screen render a combination that cannot happen — a spinner over an error, an error while
 * signed in. Every consumer of this library was otherwise deriving the same four cases from
 * `isLoading` + `error` + `isSignedIn`, and deriving them slightly differently.
 */
public sealed interface AuthPhase {

    /** Nothing has been attempted, or the last attempt was dismissed. Show the providers. */
    public data object Idle : AuthPhase

    /** A provider flow is running. Show progress; do not offer the providers again. */
    public data object InProgress : AuthPhase

    /** A session exists — anonymous or a real account. Read [SupabaseAuthUiState.session] for which. */
    public data object SignedIn : AuthPhase

    /**
     * The last attempt failed with something worth showing.
     *
     * A user-cancelled flow never reaches here: dismissing a provider sheet is a decision, not a
     * failure, and surfacing it as an error trains people to ignore errors. It returns to [Idle].
     */
    public data class Failed(public val error: AuthError) : AuthPhase
}
