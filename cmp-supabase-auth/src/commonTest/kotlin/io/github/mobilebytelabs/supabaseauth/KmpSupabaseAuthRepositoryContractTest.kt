package io.github.mobilebytelabs.supabaseauth

import io.github.mobilebytelabs.supabaseauth.testing.FakeKmpSupabaseAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KmpSupabaseAuthRepositoryContractTest {

    @Test
    fun emittedSessionBecomesSignedIn() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        repo.emitSession(KmpSupabaseAuthUser(id = "u1", email = "a@b.test"))
        assertTrue(repo.isSignedIn.value)
        assertEquals("u1", repo.currentUser.value?.id)
    }

    @Test
    fun continueAsGuestProducesAnAnonymousUser() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        val result = repo.continueAsGuest()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isAnonymous)
        assertEquals(KmpSupabaseAuthProvider.ANONYMOUS, result.getOrThrow().provider)
    }

    @Test
    fun signOutClearsTheSession() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        repo.emitSession(KmpSupabaseAuthUser(id = "u1"))
        repo.signOut()
        assertFalse(repo.isSignedIn.value)
        assertNull(repo.currentUser.value)
        assertEquals(1, repo.signOutCallCount)
    }

    @Test
    fun accessTokenTracksTheSession() = runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        assertNull(repo.accessToken())
        repo.emitSession(KmpSupabaseAuthUser(id = "u1"))
        assertEquals("fake-token", repo.accessToken())
    }
}
