package io.github.mobilebytelabs.supabaseauth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An anonymous session must read as a GUEST, not as a signed-in account.
 *
 * Regression (device-verified 2026-10-01, mbs/cappy): `KmpSupabaseAuthClientImpl` derived
 * `isAnonymous` as
 * `raw.appMetadata?.let { providerFrom(it, false) } == null && email.isBlank && phone.isBlank`.
 * `providerFrom` always returns a provider (OTHER at worst), so that first clause held only when
 * `app_metadata` was null — and an anonymous GoTrue user HAS app_metadata. Every anonymous session
 * was therefore flagged NOT anonymous, so `isGuest` was false, `isAuthenticated` was true, and the
 * Settings Account row rendered a guest as "Signed in — cloud sync is on".
 *
 * Server-side proof at the time: `select is_anonymous from auth.users` returned three `true` rows
 * while the app claimed the person held a real account.
 *
 * These assertions pin the SEMANTICS the whole stack keys off, independent of how the flag is
 * derived — a consumer's "is there a real account behind this?" predicate is built from them.
 */
class KmpSupabaseAnonymousSessionTest {

    private fun session(user: KmpSupabaseAuthUser?) =
        KmpSupabaseAuthSession(user = user, isSignedIn = user != null)

    @Test
    fun anAnonymousUserIsAGuestAndNotAuthenticated() {
        val s = session(KmpSupabaseAuthUser(id = "anon-1", isAnonymous = true))
        assertTrue(s.isSignedIn, "an anonymous session IS a session")
        assertTrue(s.isGuest, "anonymous must read as guest")
        assertFalse(s.isAuthenticated, "anonymous is NOT a real account — this is the bug that shipped")
    }

    @Test
    fun aProviderUserIsAuthenticatedAndNotAGuest() {
        val s = session(
            KmpSupabaseAuthUser(
                id = "u-1",
                email = "a@b.test",
                provider = KmpSupabaseAuthProvider.GOOGLE,
                isAnonymous = false,
            ),
        )
        assertTrue(s.isAuthenticated)
        assertFalse(s.isGuest)
    }

    @Test
    fun signedOutIsNeitherGuestNorAuthenticated() {
        val s = KmpSupabaseAuthSession.SignedOut
        assertFalse(s.isSignedIn)
        assertFalse(s.isGuest, "signed out is NOT anonymous — the two are distinct states")
        assertFalse(s.isAuthenticated)
    }

    /** The predicate a "sign in to save your progress" prompt actually wants. */
    @Test
    fun hasNoAccountCoversBothAnonymousAndSignedOut() {
        val anon = session(KmpSupabaseAuthUser(id = "anon-1", isAnonymous = true))
        val out = KmpSupabaseAuthSession.SignedOut
        val real = session(KmpSupabaseAuthUser(id = "u-1", email = "a@b.test", isAnonymous = false))
        assertTrue(!anon.isAuthenticated, "anonymous has no real account")
        assertTrue(!out.isAuthenticated, "signed out has no real account")
        assertFalse(!real.isAuthenticated, "a provider account does")
    }
}
