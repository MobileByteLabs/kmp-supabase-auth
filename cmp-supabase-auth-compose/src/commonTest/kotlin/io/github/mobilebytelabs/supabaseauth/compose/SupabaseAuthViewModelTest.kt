package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.AuthError
import io.github.mobilebytelabs.supabaseauth.AuthUser
import io.github.mobilebytelabs.supabaseauth.testing.FakeAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseAuthViewModelTest {

    // UnconfinedTestDispatcher, not the runTest default: the ViewModel starts collecting the
    // session in `init`, and under StandardTestDispatcher that collector is merely SCHEDULED —
    // so a session emitted before the first advance is never observed and every assertion about
    // signed-in state fails for a reason that has nothing to do with the code under test.
    private fun TestScope.viewModel(repo: FakeAuthRepository) =
        SupabaseAuthViewModel(repo, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    /**
     * THE load-bearing test.
     *
     * On Android the native-Google `onResult(Success)` callback frequently never fires even
     * though the id_token exchange succeeded and the session landed. This test reaches signed-in
     * with NO callback invoked at all — if someone ever rewires the ViewModel to key on the
     * callback, this fails, and the spinner-forever bug is caught here rather than on a device.
     */
    @Test
    fun reachesSignedInFromTheSessionAloneWithNoSuccessCallback() = runTest {
        val repo = FakeAuthRepository()
        val vm = viewModel(repo)

        vm.onSignInStarted()
        assertTrue(vm.state.value.isLoading)

        repo.emitSession(AuthUser(id = "u1", email = "a@b.test"))
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isSignedIn)
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.user)
    }

    @Test
    fun failureClearsLoadingAndSurfacesTheError() = runTest {
        val vm = viewModel(FakeAuthRepository())
        vm.onSignInStarted()
        vm.onSignInFailed(AuthError.Network())
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.error)
    }

    /** Dismissing the provider sheet is not a failure and must not render as one. */
    @Test
    fun cancellationClearsLoadingWithoutShowingAnError() = runTest {
        val vm = viewModel(FakeAuthRepository())
        vm.onSignInStarted()
        vm.onSignInFailed(AuthError.Cancelled)
        testScheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun continueAsGuestReachesAnonymousSignedInState() = runTest {
        val vm = viewModel(FakeAuthRepository())
        vm.continueAsGuest()
        testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isSignedIn)
        assertTrue(vm.state.value.user?.isAnonymous == true)
    }

    @Test
    fun dismissErrorClearsIt() = runTest {
        val vm = viewModel(FakeAuthRepository())
        vm.onSignInFailed(AuthError.Network())
        vm.dismissError()
        assertNull(vm.state.value.error)
    }
}
