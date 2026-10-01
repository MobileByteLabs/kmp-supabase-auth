package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The store must mirror the client WITHOUT the consumer calling [AuthSessionStore.start].
 *
 * Regression (device-verified 2026-10-01, mbs/cappy): `supabaseAuthStore()` built a
 * `DefaultAuthSessionStore` and never started it, and nothing else in the library did either
 * (`grep '\.start('` over both modules returned zero hits). `AuthRepository.session` therefore sat
 * at [AuthSession.SignedOut] for the life of the process while the underlying GoTrue client was
 * fully `Authenticated` — Google sign-in completed, the session was imported and persisted, and the
 * app still showed "Signing in…" forever and "You're a guest right now" in settings.
 *
 * A module that hands back a silently inert object is the defect: `start()` being documented as
 * "call once, eagerly" is not enough when the library's OWN DI module is the thing that forgets.
 * These tests pin the store as self-starting so the lifecycle cannot be forgotten again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthSessionStoreStartTest {

    @Test
    fun mirrorsTheClientWithoutAnExplicitStart() = runTest {
        val client = FakeSupabaseAuthClient()
        val store: AuthSessionStore = DefaultAuthSessionStore(client, backgroundScope)
        runCurrent()

        client.emit(AuthUser(id = "u1", email = "a@b.test"))
        runCurrent()

        assertTrue(store.isSignedIn.value, "store must observe the client with no start() call")
        assertEquals("u1", store.user.value?.id)
        assertEquals("u1", store.session.value.user?.id)
        assertTrue(store.session.value.isSignedIn)
    }

    /** `start()` stays on the interface, so calling it must not double-subscribe or reset state. */
    @Test
    fun anExplicitStartIsIdempotent() = runTest {
        val client = FakeSupabaseAuthClient()
        val store: AuthSessionStore = DefaultAuthSessionStore(client, backgroundScope)
        store.start(backgroundScope)
        store.start(backgroundScope)
        runCurrent()

        client.emit(AuthUser(id = "u2"))
        runCurrent()

        assertEquals("u2", store.user.value?.id)
        assertEquals(1, client.collectorCount, "start() must not add a second collector")
    }

    @Test
    fun signedOutEmissionClearsTheSession() = runTest {
        val client = FakeSupabaseAuthClient()
        val store: AuthSessionStore = DefaultAuthSessionStore(client, backgroundScope)
        runCurrent()

        client.emit(AuthUser(id = "u1"))
        runCurrent()
        client.emit(null)
        runCurrent()

        assertNull(store.user.value)
        assertEquals(AuthSession.SignedOut, store.session.value)
    }
}

private class FakeSupabaseAuthClient : SupabaseAuthClient {
    private val users = MutableStateFlow<AuthUser?>(null)
    var collectorCount = 0
        private set

    fun emit(user: AuthUser?) {
        users.value = user
    }

    override val isConfigured: Boolean = true
    override val raw: SupabaseClient? = null
    override val sessionStatus: Flow<SessionStatus>? = null

    override val currentUser: Flow<AuthUser?> = object : Flow<AuthUser?> {
        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<AuthUser?>) {
            collectorCount++
            users.collect(collector)
        }
    }

    override val isSignedIn: Flow<Boolean> = users.map { it != null }

    override suspend fun signInAnonymously(): Result<Unit> = Result.success(Unit)
    override suspend fun signInWithAppleFallback(): Result<Unit> = Result.success(Unit)
    override suspend fun signInWithGoogleFallback(): Result<Unit> = Result.success(Unit)
    override suspend fun hasRestorableSession(): Boolean = users.value != null
    override fun currentAccessToken(): String? = users.value?.let { "token-${it.id}" }
    override suspend fun signOut(): Result<Unit> {
        users.value = null
        return Result.success(Unit)
    }
}
