package io.github.mobilebytelabs.supabaseauth

/**
 * The whole answer to "who is using the app right now", as one value.
 *
 * [AuthRepository] exposes `currentUser` and `isSignedIn` separately, which is convenient for a
 * single `collectAsState` but lets a caller read them a frame apart and act on a pair that never
 * actually existed — signed-in with a null user, or the reverse. Collecting [AuthRepository.session]
 * gives one flow whose value is always internally consistent.
 *
 * Three states, not two. A consumer that models only "signed in or not" cannot tell an anonymous
 * session from a real account, and anonymous is the state most likely to need different UI — it is
 * upgradeable, and its id survives the upgrade.
 *
 * Deliberately carries identity ONLY. Entitlements (is this person a subscriber?), profile rows and
 * app preferences belong to the app, which already has a source of truth for them; a second copy
 * here would be a second answer that can disagree.
 */
public data class AuthSession(
    /** The signed-in identity, or null when nobody is signed in. */
    public val user: AuthUser? = null,
    /** True while a session exists — anonymous or otherwise. */
    public val isSignedIn: Boolean = false,
) {
    /** An anonymous session: a real, upgradeable session with no provider identity behind it. */
    public val isGuest: Boolean get() = user != null && user.isAnonymous

    /** Signed in through a real provider — Google, Apple, email. Never true for a guest. */
    public val isAuthenticated: Boolean get() = isSignedIn && user != null && !user.isAnonymous

    /** Nobody is signed in — not even anonymously. */
    public val isSignedOut: Boolean get() = user == null

    public companion object {
        /** The pre-sign-in / signed-out value. */
        public val SignedOut: AuthSession = AuthSession()
    }
}
