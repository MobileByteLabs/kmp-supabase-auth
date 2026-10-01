package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.AuthError
import io.github.mobilebytelabs.supabaseauth.AuthProvider
import io.github.mobilebytelabs.supabaseauth.AuthUser
import io.github.mobilebytelabs.supabaseauth.testing.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthPhaseTest {

    private fun vm(repo: FakeAuthRepository, configured: Boolean = true) =
        SupabaseAuthViewModel(repo, TestScope(UnconfinedTestDispatcher()), isConfigured = configured)

    private val account = AuthUser(id = "u-1", email = "a@b.c", provider = AuthProvider.GOOGLE)
    private val guest = AuthUser(id = "anon-1", provider = AuthProvider.ANONYMOUS, isAnonymous = true)

    @Test
    fun starts_idle_and_offers_sign_in() {
        val s = vm(FakeAuthRepository()).state.value
        assertEquals(AuthPhase.Idle, s.phase)
        assertTrue(s.canSignIn)
        assertNull(s.error)
    }

    /** The states are mutually exclusive — the reason this is a phase and not three booleans. */
    @Test
    fun in_progress_is_not_signed_in_and_not_failed() {
        val repo = FakeAuthRepository()
        val v = vm(repo)
        v.onSignInStarted()
        val s = v.state.value
        assertEquals(AuthPhase.InProgress, s.phase)
        assertTrue(s.isLoading)
        assertFalse(s.isSignedIn)
        assertNull(s.error)
        assertFalse(s.canSignIn, "a flow is already running")
    }

    @Test
    fun network_failure_is_reported_as_offline() {
        val v = vm(FakeAuthRepository())
        v.onSignInStarted()
        v.onSignInFailed(AuthError.Network())
        val s = v.state.value
        assertTrue(s.phase is AuthPhase.Failed)
        assertTrue(s.isOffline, "connectivity failure needs retry, not 'something went wrong'")
        assertFalse(s.isLoading)
    }

    @Test
    fun non_network_failure_is_not_offline() {
        val v = vm(FakeAuthRepository())
        v.onSignInFailed(AuthError.ProviderRejected(AuthProvider.APPLE))
        assertFalse(v.state.value.isOffline)
        assertTrue(v.state.value.phase is AuthPhase.Failed)
    }

    /** A landed session must clear a stale failure — otherwise the screen shows an error to someone signed in. */
    @Test
    fun signing_in_after_a_failure_clears_the_failure() = runTest {
        val repo = FakeAuthRepository()
        val v = vm(repo)
        v.onSignInFailed(AuthError.Network())
        assertTrue(v.state.value.phase is AuthPhase.Failed)

        repo.emitSession(account)
        val s = v.state.value
        assertEquals(AuthPhase.SignedIn, s.phase)
        assertNull(s.error)
        assertTrue(s.isAuthenticated)
    }

    @Test
    fun signing_out_returns_to_idle_not_signed_in() = runTest {
        val repo = FakeAuthRepository()
        val v = vm(repo)
        repo.emitSession(account)
        assertEquals(AuthPhase.SignedIn, v.state.value.phase)

        repo.emitSession(null)
        assertEquals(AuthPhase.Idle, v.state.value.phase)
        assertFalse(v.state.value.isSignedIn)
    }

    /** Anonymous is signed IN but not authenticated — the distinction UI needs. */
    @Test
    fun guest_session_is_signed_in_but_not_authenticated() = runTest {
        val repo = FakeAuthRepository()
        val v = vm(repo)
        repo.emitSession(guest)
        val s = v.state.value
        assertEquals(AuthPhase.SignedIn, s.phase)
        assertTrue(s.isSignedIn)
        assertTrue(s.isGuest)
        assertFalse(s.isAuthenticated)
    }

    @Test
    fun unconfigured_cannot_sign_in_but_is_not_an_error() {
        val s = vm(FakeAuthRepository(), configured = false).state.value
        assertFalse(s.canSignIn)
        assertNull(s.error, "an app can be usable offline; unconfigured is not a failure")
        assertEquals(AuthPhase.Idle, s.phase)
    }

    @Test
    fun dismissing_a_failure_returns_to_idle_and_re_offers_sign_in() {
        val v = vm(FakeAuthRepository())
        v.onSignInFailed(AuthError.Unknown())
        v.dismissError()
        assertEquals(AuthPhase.Idle, v.state.value.phase)
        assertTrue(v.state.value.canSignIn)
    }
}
