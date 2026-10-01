package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthUser
import io.github.mobilebytelabs.supabaseauth.testing.FakeKmpSupabaseAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The failure this pins was observed on a real device: native Google was launched, Credential
 * Manager showed no UI, and `onResult` never fired — leaving "Signing in…" on screen indefinitely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KmpSupabaseSignInTimeoutTest {

    // `backgroundScope`, not `this`: the ViewModel collects `repository.session` forever, and
    // runTest waits for every child of its own scope — so passing `this` hangs the test on a
    // collector that is never meant to finish. backgroundScope shares the virtual clock (so
    // advanceTimeBy still drives the timeout) but is torn down rather than awaited.

    private val account = KmpSupabaseAuthUser(id = "u-1", email = "a@b.c", provider = KmpSupabaseAuthProvider.GOOGLE)

    @Test
    fun a_provider_that_never_answers_fails_instead_of_hanging() = runTest {
        val vm = KmpSupabaseAuthViewModel(FakeKmpSupabaseAuthRepository(), backgroundScope, signInTimeout = 30.seconds)
        vm.onSignInStarted()
        assertEquals(KmpSupabaseAuthPhase.InProgress, vm.state.value.phase)

        advanceTimeBy(31.seconds)

        val phase = vm.state.value.phase
        assertIs<KmpSupabaseAuthPhase.Failed>(phase, "still InProgress — the screen would hang forever")
        assertEquals(KmpSupabaseAuthError.NoResponse, phase.error)
        assertTrue(vm.state.value.canSignIn, "the user must be able to try again")
    }

    @Test
    fun it_does_not_fire_while_the_provider_is_still_plausibly_working() = runTest {
        val vm = KmpSupabaseAuthViewModel(FakeKmpSupabaseAuthRepository(), backgroundScope, signInTimeout = 30.seconds)
        vm.onSignInStarted()
        advanceTimeBy(29.seconds)
        assertEquals(KmpSupabaseAuthPhase.InProgress, vm.state.value.phase, "fired too early — a real sheet may be open")
    }

    /** The success path must not be clobbered by a late timeout. */
    @Test
    fun a_landed_session_cancels_the_timeout() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        val vm = KmpSupabaseAuthViewModel(repo, backgroundScope, signInTimeout = 30.seconds)
        vm.onSignInStarted()
        repo.emitSession(account)
        // The collector runs on the test scheduler, so let it drain before asserting — otherwise
        // this reads the state from BEFORE the session arrived and fails for the wrong reason.
        runCurrent()
        assertEquals(KmpSupabaseAuthPhase.SignedIn, vm.state.value.phase)

        advanceTimeBy(60.seconds)
        assertEquals(KmpSupabaseAuthPhase.SignedIn, vm.state.value.phase, "the timeout overwrote a real session")
    }

    /** A reported failure must not be replaced by the generic NoResponse. */
    @Test
    fun a_reported_failure_cancels_the_timeout() = runTest {
        val vm = KmpSupabaseAuthViewModel(FakeKmpSupabaseAuthRepository(), backgroundScope, signInTimeout = 30.seconds)
        vm.onSignInStarted()
        vm.onSignInFailed(KmpSupabaseAuthError.Network())

        advanceTimeBy(60.seconds)
        val phase = vm.state.value.phase
        assertIs<KmpSupabaseAuthPhase.Failed>(phase)
        assertIs<KmpSupabaseAuthError.Network>(phase.error, "NoResponse overwrote the real cause")
    }

    /** A second attempt restarts the clock rather than inheriting the first one's. */
    @Test
    fun a_new_attempt_rearms_the_timeout() = runTest {
        val vm = KmpSupabaseAuthViewModel(FakeKmpSupabaseAuthRepository(), backgroundScope, signInTimeout = 30.seconds)
        vm.onSignInStarted()
        advanceTimeBy(25.seconds)
        vm.onSignInStarted()
        advanceTimeBy(25.seconds)
        assertEquals(KmpSupabaseAuthPhase.InProgress, vm.state.value.phase, "inherited the first attempt's clock")
        advanceTimeBy(10.seconds)
        assertIs<KmpSupabaseAuthPhase.Failed>(vm.state.value.phase)
    }

    @Test
    fun infinite_disables_it() = runTest {
        val vm = KmpSupabaseAuthViewModel(FakeKmpSupabaseAuthRepository(), backgroundScope, signInTimeout = Duration.INFINITE)
        vm.onSignInStarted()
        advanceTimeBy(600.seconds)
        assertEquals(KmpSupabaseAuthPhase.InProgress, vm.state.value.phase)
    }
}
