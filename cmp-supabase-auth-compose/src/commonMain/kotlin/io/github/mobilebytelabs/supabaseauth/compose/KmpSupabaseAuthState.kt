package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthRepository
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthSession
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthUser
import org.koin.compose.koinInject

/**
 * Who is signed in, right now, as one value a screen can read directly.
 *
 * **This type exists because every consumer was re-deriving it, and deriving it differently.**
 * Observed in a real app:
 *
 * ```kotlin
 * val isGuest by authRepository.isAuthenticated
 *     .map { !it }
 *     .collectAsStateWithLifecycle(initialValue = true)
 * ```
 *
 * Three separate decisions are buried in those three lines — that "guest" means `!isAuthenticated`,
 * that the pre-first-emission default is `true`, and that the mapping happens in composition — and
 * each one was spelled differently at each call site. The predicate in particular is easy to get
 * backwards: `isGuest` on the session means an ANONYMOUS session specifically, while a screen
 * usually wants "no real account", which also covers signed-out. Both readings are available here
 * under names that say which is which, so a screen never has to invent one.
 */
@Immutable
public data class KmpSupabaseAuthState(
    /** The whole session. [isGuest]/[isAuthenticated] below are named reads of it. */
    public val session: KmpSupabaseAuthSession = KmpSupabaseAuthSession.SignedOut,
) {
    /** The signed-in identity, or null. */
    public val user: KmpSupabaseAuthUser? get() = session.user

    /** A session exists — anonymous or a real account. */
    public val isSignedIn: Boolean get() = session.isSignedIn

    /** A real provider account. False for an anonymous session AND when signed out. */
    public val isAuthenticated: Boolean get() = session.isAuthenticated

    /** An ANONYMOUS session specifically. False when signed out — see [hasNoAccount]. */
    public val isGuest: Boolean get() = session.isGuest

    /**
     * No real account: anonymous OR signed out.
     *
     * This is what a "sign in to save your progress" prompt actually wants, and it is the one
     * [isGuest] gets confused with — `isGuest` alone hides the prompt from the never-signed-in
     * person it is most aimed at.
     */
    public val hasNoAccount: Boolean get() = !isAuthenticated
}

/**
 * Live auth state, resolved from DI. Recomposes on every session change.
 *
 * ```kotlin
 * val auth = rememberKmpSupabaseAuthState()
 * if (auth.hasNoAccount) SignInPrompt(onClick = …)
 * ```
 *
 * Takes the repository from Koin so a screen does not have to inject, hold, or thread it — the
 * binding is already in the graph from `kmpSupabaseAuthRepository()`. Pass [repository] explicitly
 * in a preview or a test, where there is no graph.
 */
@Composable
public fun rememberKmpSupabaseAuthState(repository: KmpSupabaseAuthRepository = koinInject()): KmpSupabaseAuthState {
    // `session` is a StateFlow with a real current value, so collectAsState needs no initial —
    // which removes the other thing every call site was choosing differently and sometimes wrong
    // (an `initialValue = true` for isGuest renders a one-frame guest state for a signed-in user).
    val session by repository.session.collectAsState()
    return remember(session) { KmpSupabaseAuthState(session) }
}

/** [KmpSupabaseAuthState.hasNoAccount] alone, for a screen that needs only the predicate. */
@Composable
public fun rememberKmpSupabaseHasNoAccount(repository: KmpSupabaseAuthRepository = koinInject()): State<Boolean> {
    val state = rememberKmpSupabaseAuthState(repository)
    return remember(state.hasNoAccount) {
        object : State<Boolean> {
            override val value: Boolean = state.hasNoAccount
        }
    }
}
