package io.github.mobilebytelabs.supabaseauth

import io.github.mobilebytelabs.supabaseauth.testing.FakeKmpSupabaseAuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmpSupabaseAuthSessionTest {

    private val guest = KmpSupabaseAuthUser(id = "anon-1", provider = KmpSupabaseAuthProvider.ANONYMOUS, isAnonymous = true)
    private val account = KmpSupabaseAuthUser(id = "u-1", email = "a@b.c", provider = KmpSupabaseAuthProvider.GOOGLE)

    @Test
    fun signed_out_is_neither_guest_nor_authenticated() {
        val s = KmpSupabaseAuthSession.SignedOut
        assertTrue(s.isSignedOut)
        assertFalse(s.isSignedIn)
        assertFalse(s.isGuest)
        assertFalse(s.isAuthenticated)
    }

    /** The distinction the two-boolean shape cannot express: a real session with no account. */
    @Test
    fun anonymous_is_signed_in_and_guest_but_not_authenticated() {
        val s = KmpSupabaseAuthSession(user = guest, isSignedIn = true)
        assertTrue(s.isSignedIn)
        assertTrue(s.isGuest)
        assertFalse(s.isAuthenticated)
        assertFalse(s.isSignedOut)
    }

    @Test
    fun provider_account_is_authenticated_and_not_guest() {
        val s = KmpSupabaseAuthSession(user = account, isSignedIn = true)
        assertTrue(s.isAuthenticated)
        assertFalse(s.isGuest)
        assertFalse(s.isSignedOut)
    }

    /**
     * The reason this type exists: `session` must never disagree with `currentUser`/`isSignedIn`.
     * Asserted across every transition, because a torn read is exactly what a single snapshot misses.
     */
    @Test
    fun session_always_agrees_with_currentUser_and_isSignedIn() {
        val repo = FakeKmpSupabaseAuthRepository()
        listOf(null, guest, account, null, account).forEach { u ->
            repo.emitSession(u)
            assertEquals(u, repo.session.value.user, "user disagrees after emitting $u")
            assertEquals(repo.currentUser.value, repo.session.value.user, "session vs currentUser")
            assertEquals(repo.isSignedIn.value, repo.session.value.isSignedIn, "session vs isSignedIn")
        }
    }

    @Test
    fun sign_out_returns_the_session_to_signed_out() = kotlinx.coroutines.test.runTest {
        val repo = FakeKmpSupabaseAuthRepository()
        repo.emitSession(account)
        assertTrue(repo.session.value.isAuthenticated)
        repo.signOut()
        assertEquals(KmpSupabaseAuthSession.SignedOut, repo.session.value)
    }
}
