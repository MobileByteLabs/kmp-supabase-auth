package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider

/**
 * Everything a sign-in screen can ask for, as one closed set.
 *
 * **This vocabulary was being re-declared by every consumer.** Observed verbatim in a real app:
 * `SignInGoogle`, `SignInApple`, `RetrySignIn`, `ContinueOffline`, `SignInSucceeded`,
 * `SignInFailed`, `SignInOffline`, `SignInDismissed` — eight cases, none of them app-specific, all
 * of them re-deriving the same state machine the library already drives. Worse, the result cases
 * (`SignInSucceeded` / `SignInFailed`) invited apps to treat a provider CALLBACK as the source of
 * truth for success, which is the single behaviour this library most needs them not to do: on
 * Android the native Google success callback frequently never fires even though the session landed.
 *
 * So this set carries only INTENTS — what the person asked for. Outcomes are not actions, because
 * the outcome is read from the session, never dispatched from a callback.
 */
public sealed interface KmpSupabaseSignInAction {

    /** Start a provider flow. One case for every provider, rather than one case per provider. */
    public data class SignIn(public val provider: KmpSupabaseAuthProvider) : KmpSupabaseSignInAction

    /**
     * Retry the last attempt.
     *
     * Distinct from re-dispatching [SignIn] so the UI can offer "Try again" without re-deciding
     * which provider was used, and so analytics can tell a first attempt from a second.
     */
    public data object Retry : KmpSupabaseSignInAction

    /** Create an anonymous session and proceed without an account. */
    public data object ContinueAsGuest : KmpSupabaseSignInAction

    /** Dismiss a reported failure and offer the providers again. */
    public data object DismissError : KmpSupabaseSignInAction

    /** End the current session. */
    public data object SignOut : KmpSupabaseSignInAction

    public companion object {
        /** `SignIn(GOOGLE)`, spelled the way a screen reads. */
        public val Google: KmpSupabaseSignInAction = SignIn(KmpSupabaseAuthProvider.GOOGLE)

        /** `SignIn(APPLE)`, spelled the way a screen reads. */
        public val Apple: KmpSupabaseSignInAction = SignIn(KmpSupabaseAuthProvider.APPLE)
    }
}
