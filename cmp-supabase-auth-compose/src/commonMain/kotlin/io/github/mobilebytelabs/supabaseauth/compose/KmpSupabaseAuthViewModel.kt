package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthRepository
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthSession
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthUser
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Everything a sign-in screen needs to render, as one value.
 *
 * [phase] and [session] are the state; the rest are named reads of them, so a screen never has to
 * combine two fields and never sees an impossible pair. Consumers were each deriving these from
 * `isLoading` + `error` + a user — the same derivation, written slightly differently in every app.
 */
public data class KmpSupabaseAuthUiState(
    /** Where the screen is: [KmpSupabaseAuthPhase.Idle], [KmpSupabaseAuthPhase.InProgress], [KmpSupabaseAuthPhase.SignedIn], [KmpSupabaseAuthPhase.Failed]. */
    public val phase: KmpSupabaseAuthPhase = KmpSupabaseAuthPhase.Idle,
    /** Who is signed in, if anyone. Identity only. */
    public val session: KmpSupabaseAuthSession = KmpSupabaseAuthSession.SignedOut,
    /**
     * False when the project ref or anon key is a placeholder.
     *
     * Not a failure state — an app can be perfectly usable offline with cloud sign-in disabled, so
     * this disables the providers rather than showing an error. Pass `client.isConfigured`.
     */
    public val isConfigured: Boolean = true,
) {
    /** A provider flow is running. */
    public val isLoading: Boolean get() = phase is KmpSupabaseAuthPhase.InProgress

    /** The error worth showing, or null. Never a user cancellation. */
    public val error: KmpSupabaseAuthError? get() = (phase as? KmpSupabaseAuthPhase.Failed)?.error

    /**
     * The failure was connectivity.
     *
     * Worth its own read because it is the one failure with a different remedy — retry, rather
     * than "something went wrong". Apps routinely gave it a separate screen state.
     */
    public val isOffline: Boolean get() = error is KmpSupabaseAuthError.Network

    /** Sign-in is possible: configured, and not already in flight. */
    public val canSignIn: Boolean get() = isConfigured && phase !is KmpSupabaseAuthPhase.InProgress

    public val user: KmpSupabaseAuthUser? get() = session.user

    /** A session exists — anonymous or a real account. */
    public val isSignedIn: Boolean get() = session.isSignedIn

    /** A real provider account. False for a guest. */
    public val isAuthenticated: Boolean get() = session.isAuthenticated

    /** An anonymous session. NOT "signed out". */
    public val isGuest: Boolean get() = session.isGuest
}

/**
 * Drives a login screen.
 *
 * **Signed-in is derived from the repository's session flow, never from a provider callback.**
 * That is not a style preference. On Android the native-Google success callback frequently never
 * fires even though the exchange succeeded, so a callback-driven ViewModel leaves the person on a
 * spinner while they are in fact authenticated. This is the single most load-bearing behaviour in
 * the library, and `KmpSupabaseAuthViewModelTest` pins it by reaching signed-in with no callback at all.
 *
 * @param isConfigured pass `client.isConfigured`; when false the providers render disabled.
 */
public class KmpSupabaseAuthViewModel(
    private val repository: KmpSupabaseAuthRepository,
    private val scope: CoroutineScope,
    isConfigured: Boolean = true,
    /**
     * How long to wait for a provider before giving up on it.
     *
     * A provider can return NOTHING — no success, no error, no cancellation. Observed on Android
     * with native Google when the build's signing certificate is not registered against the OAuth
     * client: Credential Manager shows no UI and never calls back, so `onResult` never fires.
     * Without this, [KmpSupabaseAuthPhase.InProgress] has no exit and the screen shows "Signing in…" forever.
     *
     * The library already derives SUCCESS from the session rather than the callback, for the same
     * reason in the other direction. This closes the failure half.
     *
     * 60s is deliberately generous: a real provider sheet can sit open while someone finds their
     * password, and timing that out would be worse than the bug. Set [kotlin.time.Duration.INFINITE]
     * to disable.
     */
    private val signInTimeout: Duration = 60.seconds,
) {
    private var timeoutJob: Job? = null

    private val _state = MutableStateFlow(KmpSupabaseAuthUiState(isConfigured = isConfigured))
    public val state: StateFlow<KmpSupabaseAuthUiState> = _state.asStateFlow()

    init {
        repository.session
            .onEach { session ->
                // A landed session is the definitive answer; stop waiting for a callback that the
                // provider may never send.
                if (session.isSignedIn) timeoutJob?.cancel()
                KmpSupabaseAuthLog.log {
                    "session: signedIn=${session.isSignedIn} guest=${session.isGuest} " +
                        "authenticated=${session.isAuthenticated}"
                }
                val current = _state.value.phase
                _state.value = _state.value.copy(
                    session = session,
                    phase = when {
                        // A landed session ends InProgress regardless of any callback, and clears
                        // a stale failure from a previous attempt.
                        session.isSignedIn -> KmpSupabaseAuthPhase.SignedIn

                        // A signed-out emission must NOT cancel an attempt that is still running.
                        // `repository.session` is a StateFlow: it replays its current value to a
                        // new subscriber and re-emits on any upstream change, so mapping every
                        // signed-out emission to Idle wiped InProgress mid-flow and the spinner
                        // vanished while the provider sheet was still open.
                        current is KmpSupabaseAuthPhase.InProgress -> current

                        // Likewise keep a reported failure visible; a signed-out session is not
                        // new information about it.
                        current is KmpSupabaseAuthPhase.Failed -> current

                        // Signing OUT returns to Idle rather than leaving the screen claiming
                        // SignedIn.
                        else -> KmpSupabaseAuthPhase.Idle
                    },
                )
            }
            .launchIn(scope)
    }

    /** Call immediately before launching a provider flow. */
    public fun onSignInStarted() {
        KmpSupabaseAuthLog.log { "phase: ${_state.value.phase} -> InProgress" }
        _state.value = _state.value.copy(phase = KmpSupabaseAuthPhase.InProgress)
        armTimeout()
    }

    /**
     * Fail the attempt if the provider never answers.
     *
     * Cancelled by the next state change — a landed session, a reported failure, or another
     * attempt — so it only fires when NOTHING came back.
     */
    private fun armTimeout() {
        timeoutJob?.cancel()
        if (signInTimeout == Duration.INFINITE) return
        timeoutJob = scope.launch {
            delay(signInTimeout)
            if (_state.value.phase is KmpSupabaseAuthPhase.InProgress) {
                KmpSupabaseAuthLog.log {
                    "TIMEOUT after $signInTimeout — the provider never called back (no success, " +
                        "no error, no cancellation). Check that this build's signing certificate " +
                        "is registered against the OAuth client."
                }
                _state.value = _state.value.copy(phase = KmpSupabaseAuthPhase.Failed(KmpSupabaseAuthError.NoResponse))
            }
        }
    }

    /** Wire to a launcher's `onError`. */
    public fun onSignInFailed(error: KmpSupabaseAuthError) {
        timeoutJob?.cancel()
        KmpSupabaseAuthLog.logError(error.cause) { "sign-in failed: ${error::class.simpleName}" }
        _state.value = _state.value.copy(
            // Dismissing the provider sheet is a decision, not a failure — back to Idle so the
            // screen offers the providers again instead of showing an error nobody caused.
            phase = if (error is KmpSupabaseAuthError.Cancelled) KmpSupabaseAuthPhase.Idle else KmpSupabaseAuthPhase.Failed(error),
        )
    }

    public fun continueAsGuest() {
        onSignInStarted()
        scope.launch {
            repository.continueAsGuest()
                .onFailure { onSignInFailed(it as? KmpSupabaseAuthError ?: KmpSupabaseAuthError.Unknown(it)) }
        }
    }

    /** Web-OAuth sign-in, for platforms with no native provider. */
    public fun signInWithFallback(provider: KmpSupabaseAuthProvider) {
        onSignInStarted()
        scope.launch {
            repository.signInWithFallback(provider)
                .onFailure { onSignInFailed(it as? KmpSupabaseAuthError ?: KmpSupabaseAuthError.Unknown(it)) }
        }
    }

    public fun signOut() {
        scope.launch { repository.signOut() }
    }

    /** Dismiss a [KmpSupabaseAuthPhase.Failed] and offer the providers again. */
    public fun dismissError() {
        if (_state.value.phase is KmpSupabaseAuthPhase.Failed) {
            _state.value = _state.value.copy(phase = KmpSupabaseAuthPhase.Idle)
        }
    }
}
