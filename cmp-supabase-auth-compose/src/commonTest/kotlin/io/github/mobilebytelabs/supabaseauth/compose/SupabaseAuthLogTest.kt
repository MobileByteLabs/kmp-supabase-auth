package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.AuthError
import io.github.mobilebytelabs.supabaseauth.AuthProvider
import io.github.mobilebytelabs.supabaseauth.AuthUser
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthLog
import io.github.mobilebytelabs.supabaseauth.testing.FakeAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseAuthLogTest {

    private val lines = mutableListOf<String>()

    @AfterTest
    fun tearDown() {
        SupabaseAuthLog.handler = null
    }

    private fun capture() {
        lines.clear()
        SupabaseAuthLog.handler = { lines += it }
    }

    @Test
    fun off_by_default_so_an_app_that_never_opts_in_pays_nothing() {
        SupabaseAuthLog.handler = null
        assertFalse(SupabaseAuthLog.isEnabled)
        var built = false
        SupabaseAuthLog.log { built = true; "expensive" }
        assertFalse(built, "the message lambda must not run when logging is off")
    }

    @Test
    fun phase_transitions_are_logged() {
        capture()
        val vm = SupabaseAuthViewModel(FakeAuthRepository(), TestScope(UnconfinedTestDispatcher()))
        vm.onSignInStarted()
        assertTrue(lines.any { it.contains("-> InProgress") }, "actual: $lines")
    }

    /** The stuck-spinner case: a failure must leave a line, so silence means "callback never fired". */
    @Test
    fun failures_are_logged_with_their_type() {
        capture()
        val vm = SupabaseAuthViewModel(FakeAuthRepository(), TestScope(UnconfinedTestDispatcher()))
        vm.onSignInFailed(AuthError.Network())
        assertTrue(lines.any { it.contains("sign-in failed") && it.contains("Network") }, "actual: $lines")
    }

    @Test
    fun session_changes_are_logged_with_the_three_states() = runTest {
        capture()
        val repo = FakeAuthRepository()
        SupabaseAuthViewModel(repo, TestScope(UnconfinedTestDispatcher()))
        repo.emitSession(AuthUser(id = "u", provider = AuthProvider.GOOGLE))
        assertTrue(lines.any { it.contains("session:") && it.contains("authenticated=true") }, "actual: $lines")
    }

    @Test
    fun every_line_is_prefixed_for_grepping() {
        capture()
        SupabaseAuthLog.log { "hello" }
        assertEquals(1, lines.size)
        assertContains(lines[0], "[supabase-auth]")
    }

    /** A client id is exactly the value people paste into bug reports. */
    @Test
    fun redacted_describes_a_credential_without_printing_it() {
        val secret = "1234567890-abcdefg.apps.googleusercontent.com"
        val described = SupabaseAuthLog.redacted(secret)
        assertFalse(described.contains(secret), "the value leaked: $described")
        assertFalse(described.contains("googleusercontent"), "a recognisable fragment leaked: $described")
        assertContains(described, "${secret.length} chars")
        assertEquals("blank", SupabaseAuthLog.redacted("   "))
        assertEquals("absent", SupabaseAuthLog.redacted(null))
    }
}
