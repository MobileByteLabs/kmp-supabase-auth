package io.github.mobilebytelabs.supabaseauth.testing

import io.github.mobilebytelabs.supabaseauth.AuthProvider
import io.github.mobilebytelabs.supabaseauth.AuthRepository
import io.github.mobilebytelabs.supabaseauth.AuthSession
import io.github.mobilebytelabs.supabaseauth.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Test double, shipped in the MAIN artifact so consumers — and this library's own Compose
 * module — can test against it. A fake confined to `commonTest` would be invisible to both,
 * because Kotlin's `internal`/test scoping is per module.
 *
 * Drive it with [emitSession] to simulate GoTrue pushing a session, which is the only realistic
 * way to test the "signed-in comes from the session, not the callback" contract.
 */
public class FakeAuthRepository : AuthRepository {

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _session = MutableStateFlow(AuthSession.SignedOut)

    // Assigned inside emitSession alongside the other two, mirroring DefaultAuthSessionStore. A
    // fake whose fields can drift apart lets a test pass against a state the real implementation
    // cannot produce, which is worse than having no fake.
    override val session: StateFlow<AuthSession> = _session.asStateFlow()

    private val _isSignedIn = MutableStateFlow(false)
    override val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    override val accessTokenFlow: Flow<String?> = _currentUser.map { it?.let { "fake-token" } }

    /** Result [continueAsGuest] returns. Set to a failure to exercise the error path. */
    public var guestResult: Result<AuthUser> = Result.success(
        AuthUser(id = "anon-1", provider = AuthProvider.ANONYMOUS, isAnonymous = true),
    )

    /** Result [signInWithFallback] returns. */
    public var fallbackResult: Result<Unit> = Result.success(Unit)

    private var signOutCalls: Int = 0

    /** How many times [signOut] was called. */
    public val signOutCallCount: Int get() = signOutCalls

    /** Emit a session the way a real GoTrue `sessionStatus` collector would. */
    public fun emitSession(user: AuthUser?) {
        _currentUser.value = user
        _isSignedIn.value = user != null
        _session.value = AuthSession(user = user, isSignedIn = user != null)
    }

    override suspend fun continueAsGuest(): Result<AuthUser> = guestResult.onSuccess { emitSession(it) }

    override suspend fun signInWithFallback(provider: AuthProvider): Result<Unit> = fallbackResult

    override suspend fun signOut() {
        signOutCalls++
        emitSession(null)
    }

    override suspend fun restoreSession(): Boolean = _currentUser.value != null

    override fun accessToken(): String? = _currentUser.value?.let { "fake-token" }
}
