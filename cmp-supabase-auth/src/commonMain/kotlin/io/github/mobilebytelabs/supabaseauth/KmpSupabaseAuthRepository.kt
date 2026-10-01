package io.github.mobilebytelabs.supabaseauth

import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * The data-layer surface a feature module consumes.
 *
 * Free of Supabase types on purpose: a feature depending on this does not get `auth-kt` on its
 * compile path, so auth stays swappable behind one module.
 */
public interface KmpSupabaseAuthRepository {
    /**
     * The whole session as one consistent value — prefer this over reading [currentUser] and
     * [isSignedIn] separately, which can be observed a frame apart in a combination that never
     * actually occurred.
     */
    public val session: StateFlow<KmpSupabaseAuthSession>

    public val currentUser: StateFlow<KmpSupabaseAuthUser?>
    public val isSignedIn: StateFlow<Boolean>

    /**
     * The access token, re-emitting on every session change.
     *
     * This is what satisfies kmp-project-template's `AuthTokenSource` in ONE consumer line, so
     * the template's existing `AuthHeaderBridge` attaches the JWT to every access point
     * untouched. The library cannot bind `AuthTokenSource` itself — that type lives in the
     * template's `core-base/network`, which a standalone library must not depend on.
     */
    public val accessTokenFlow: Flow<String?>

    /** Start an anonymous session so a person can use the app before signing up. */
    public suspend fun continueAsGuest(): Result<KmpSupabaseAuthUser>

    /** Web-OAuth sign-in, for platforms where the native provider is unavailable. */
    public suspend fun signInWithFallback(provider: KmpSupabaseAuthProvider): Result<Unit>

    public suspend fun signOut()

    /** True when a persisted session was restored. Call at cold start. */
    public suspend fun restoreSession(): Boolean

    public fun accessToken(): String?
}

/**
 * How long to wait for the session store to reflect a freshly-minted anonymous session.
 *
 * Bounded rather than indefinite: if the store never emits, something is wrong with the mirror and
 * hanging the caller forever is worse than a timeout that says so.
 */
private val GuestSessionTimeout = 10.seconds

internal class DefaultKmpSupabaseAuthRepository(private val client: KmpSupabaseAuthClient, private val store: KmpSupabaseAuthSessionStore) :
    KmpSupabaseAuthRepository {

    // Delegated, not re-derived: the store assigns all three in one place, so these cannot drift.
    override val session: StateFlow<KmpSupabaseAuthSession> = store.session

    override val currentUser: StateFlow<KmpSupabaseAuthUser?> = store.user
    override val isSignedIn: StateFlow<Boolean> = store.isSignedIn

    // Derived from the session flow rather than polled: the token changes on refresh as well as
    // on sign-in/out, and AuthHeaderBridge distinctUntilChanged()s downstream anyway.
    override val accessTokenFlow: Flow<String?> =
        store.user.map { if (it == null) null else client.currentAccessToken() }

    override suspend fun continueAsGuest(): Result<KmpSupabaseAuthUser> = client.signInAnonymously().mapCatching {
        // AWAIT the user; do not sample it. `signInAnonymously()` returning means the SERVER minted
        // the session — but the store mirrors the client through a flow collector, so at this exact
        // instant `store.user.value` is still null and the old `?: throw Unknown()` turned a
        // SUCCESSFUL sign-in into a failure with no cause attached.
        //
        // Verified on device 2026-10-01: POST /auth/v1/signup returned an access token while this
        // method reported failure, which is the worst shape a bug can take — the server state and
        // the client's answer disagree, and the error carries nothing to explain it.
        withTimeout(GuestSessionTimeout) { store.user.filterNotNull().first() }
    }

    override suspend fun signInWithFallback(provider: KmpSupabaseAuthProvider): Result<Unit> = when (provider) {
        KmpSupabaseAuthProvider.GOOGLE -> client.signInWithGoogleFallback()
        KmpSupabaseAuthProvider.APPLE -> client.signInWithAppleFallback()
        else -> Result.failure(KmpSupabaseAuthError.ProviderRejected(provider))
    }

    override suspend fun signOut(): Unit = store.clear()

    override suspend fun restoreSession(): Boolean = client.hasRestorableSession()

    override fun accessToken(): String? = client.currentAccessToken()
}
