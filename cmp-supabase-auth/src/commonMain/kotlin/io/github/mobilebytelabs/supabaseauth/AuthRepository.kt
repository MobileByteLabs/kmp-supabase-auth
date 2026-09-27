package io.github.mobilebytelabs.supabaseauth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * The data-layer surface a feature module consumes.
 *
 * Free of Supabase types on purpose: a feature depending on this does not get `auth-kt` on its
 * compile path, so auth stays swappable behind one module.
 */
public interface AuthRepository {
    /**
     * The whole session as one consistent value — prefer this over reading [currentUser] and
     * [isSignedIn] separately, which can be observed a frame apart in a combination that never
     * actually occurred.
     */
    public val session: StateFlow<AuthSession>

    public val currentUser: StateFlow<AuthUser?>
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
    public suspend fun continueAsGuest(): Result<AuthUser>

    /** Web-OAuth sign-in, for platforms where the native provider is unavailable. */
    public suspend fun signInWithFallback(provider: AuthProvider): Result<Unit>

    public suspend fun signOut()

    /** True when a persisted session was restored. Call at cold start. */
    public suspend fun restoreSession(): Boolean

    public fun accessToken(): String?
}

internal class DefaultAuthRepository(private val client: SupabaseAuthClient, private val store: AuthSessionStore) :
    AuthRepository {

    // Delegated, not re-derived: the store assigns all three in one place, so these cannot drift.
    override val session: StateFlow<AuthSession> = store.session

    override val currentUser: StateFlow<AuthUser?> = store.user
    override val isSignedIn: StateFlow<Boolean> = store.isSignedIn

    // Derived from the session flow rather than polled: the token changes on refresh as well as
    // on sign-in/out, and AuthHeaderBridge distinctUntilChanged()s downstream anyway.
    override val accessTokenFlow: Flow<String?> =
        store.user.map { if (it == null) null else client.currentAccessToken() }

    override suspend fun continueAsGuest(): Result<AuthUser> = client.signInAnonymously().mapCatching {
        store.user.value ?: throw AuthError.Unknown()
    }

    override suspend fun signInWithFallback(provider: AuthProvider): Result<Unit> = when (provider) {
        AuthProvider.GOOGLE -> client.signInWithGoogleFallback()
        AuthProvider.APPLE -> client.signInWithAppleFallback()
        else -> Result.failure(AuthError.ProviderRejected(provider))
    }

    override suspend fun signOut(): Unit = store.clear()

    override suspend fun restoreSession(): Boolean = client.hasRestorableSession()

    override fun accessToken(): String? = client.currentAccessToken()
}
