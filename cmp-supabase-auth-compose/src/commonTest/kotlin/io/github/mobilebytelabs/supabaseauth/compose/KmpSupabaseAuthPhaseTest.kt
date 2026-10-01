package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthUser
import io.github.mobilebytelabs.supabaseauth.testing.FakeKmpSupabaseAuthRepository
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
class KmpSupabaseAuthPhaseTest {

    private fun vm(repo: FakeKmpSupabaseAuthRepository, configured: Boolean = true) =
        KmpSupabaseAuthViewModel(repo, TestScope(UnconfinedTestDispatcher()), isConfigured = configured)

    private val account = KmpSupabaseAuthUser(id = "u-1", email = "a@b.c", provider = KmpSupabaseAuthProvider.GOOGLE)
    private val guest = KmpSupabaseAuthUser(id = "anon-1", provider = KmpSupabaseAuthProvider.ANONYMOUS, isAnonymous = true)

    @Test
    fun starts_idle_and_offers_sign_in() {
        val s = vm(FakeKmpSupabaseAuthRepository()).state.value
        assertEquals(KmpSupabaseAuthPhase.Idle, s.phase)
        assertTrue(s.canSignIn)
        assertNull(s.error)
    }

    /** The states are mutually exclusive — the reason this is a phase and not three booleans. */
    @Test
    fun in_progress_is_not_signed_in_and_not_failed() {
        val repo = FakeKmpSupabaseAuthRepository()
        val v = vm(repo)
        v.onSignInStarted()
        val s = v.state.value
        assertEquals(KmpSupabaseAuthPhase.InProgress, s.phase)
        assertTrue(s.isLoading)
        assertFalse(s.isSignedIn)
        assertNull(s.error)
        assertFalse(s.canSignIn, "a flow is already running")
    }

    @Test
    fun network_failure_is_reported_as_offline() {
        val v = vm(FakeKmpSupabaseAuthRepository())
        v.onSignInStarted()
        v.onSignInFailed(KmpSupabaseAuthError.Network())
        val s = v.state.value
        assertTrue(s.phase is KmpSupabaseAuthPhase.Failed)
        assertTrue(s.isOffline, "connectivity failure needs retry, not 'something went wrong'")
        assertFalse(s.isLoading)
    }

    @Test
    fun non_network_failure_is_not_offline() {
        val v = vm(FakeKmpSupabaseAuthRepository())
        v.onSignInFailed(KmpSupabaseAuthError.ProviderRejected(KmpSupabaseAuthProvider.APPLE))
        assertFalse(v.state.value.isOffline)
        assertTrue(v.state.value.phase is KmpSupabaseAuthPhase.Failed)
    }

    /** A landed session must clear a stale failure — otherwise the screen shows an error to someone signed in. */
    @Test
    fun signing_in_after_a_failure_clears_the_failure() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        val v = vm(repo)
        v.onSignInFailed(KmpSupabaseAuthError.Network())
        assertTrue(v.state.value.phase is KmpSupabaseAuthPhase.Failed)

        repo.emitSession(account)
        val s = v.state.value
        assertEquals(KmpSupabaseAuthPhase.SignedIn, s.phase)
        assertNull(s.error)
        assertTrue(s.isAuthenticated)
    }

    @Test
    fun signing_out_returns_to_idle_not_signed_in() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        val v = vm(repo)
        repo.emitSession(account)
        assertEquals(KmpSupabaseAuthPhase.SignedIn, v.state.value.phase)

        repo.emitSession(null)
        assertEquals(KmpSupabaseAuthPhase.Idle, v.state.value.phase)
        assertFalse(v.state.value.isSignedIn)
    }

    /** Anonymous is signed IN but not authenticated — the distinction UI needs. */
    @Test
    fun guest_session_is_signed_in_but_not_authenticated() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        val v = vm(repo)
        repo.emitSession(guest)
        val s = v.state.value
        assertEquals(KmpSupabaseAuthPhase.SignedIn, s.phase)
        assertTrue(s.isSignedIn)
        assertTrue(s.isGuest)
        assertFalse(s.isAuthenticated)
    }

    @Test
    fun unconfigured_cannot_sign_in_but_is_not_an_error() {
        val s = vm(FakeKmpSupabaseAuthRepository(), configured = false).state.value
        assertFalse(s.canSignIn)
        assertNull(s.error, "an app can be usable offline; unconfigured is not a failure")
        assertEquals(KmpSupabaseAuthPhase.Idle, s.phase)
    }

    @Test
    fun dismissing_a_failure_returns_to_idle_and_re_offers_sign_in() {
        val v = vm(FakeKmpSupabaseAuthRepository())
        v.onSignInFailed(KmpSupabaseAuthError.Unknown())
        v.dismissError()
        assertEquals(KmpSupabaseAuthPhase.Idle, v.state.value.phase)
        assertTrue(v.state.value.canSignIn)
    }
}
