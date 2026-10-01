package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError

/**
 * Where a sign-in screen is in its lifecycle.
 *
 * A phase rather than a set of booleans because the states are mutually exclusive, and booleans
 * let a screen render a combination that cannot happen — a spinner over an error, an error while
 * signed in. Every consumer of this library was otherwise deriving the same four cases from
 * `isLoading` + `error` + `isSignedIn`, and deriving them slightly differently.
 */
public sealed interface KmpSupabaseAuthPhase {

    /** Nothing has been attempted, or the last attempt was dismissed. Show the providers. */
    public data object Idle : KmpSupabaseAuthPhase

    /** A provider flow is running. Show progress; do not offer the providers again. */
    public data object InProgress : KmpSupabaseAuthPhase

    /** A session exists — anonymous or a real account. Read [KmpSupabaseAuthUiState.session] for which. */
    public data object SignedIn : KmpSupabaseAuthPhase

    /**
     * The last attempt failed with something worth showing.
     *
     * A user-cancelled flow never reaches here: dismissing a provider sheet is a decision, not a
     * failure, and surfacing it as an error trains people to ignore errors. It returns to [Idle].
     */
    public data class Failed(public val error: KmpSupabaseAuthError) : KmpSupabaseAuthPhase
}
