package io.github.mobilebytelabs.supabaseauth.compose

import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which native failures may be retried in the browser, and which may NOT.
 *
 * Android's native Google path reports `NoCredentialException` for several ordinary situations that
 * are not the app's fault — no Android OAuth client for this package + signing SHA-1 (every fresh
 * fork), an unregistered certificate, no Google account on the device, Credential Manager's
 * dismissal cooldown. MEASURED on mbs/cappy 2026-10-12: `curh: [28444]` with seven Google accounts
 * signed in. Each one used to dead-end on [KmpSupabaseAuthError.ProviderRejected] even though the
 * web flow — which needs only the already-configured web client id — would have signed the person
 * in.
 *
 * The inverse matters just as much: a person who DISMISSED the sheet must not have a browser thrown
 * at them, so only the Error branch may hand off.
 */
class KmpSupabaseNativeFailureFallbackTest {

    private class Capture {
        var error: KmpSupabaseAuthError? = null
        var fellBack = false
    }

    private fun NativeSignInResult.route(fallbackAvailable: Boolean): Capture {
        val captured = Capture()
        reportFailure(
            provider = KmpSupabaseAuthProvider.GOOGLE,
            onError = { captured.error = it },
            onNativeUnusable = if (fallbackAvailable) ({ captured.fellBack = true }) else null,
        )
        return captured
    }

    @Test
    fun a_native_error_hands_off_to_the_web_flow_rather_than_dead_ending() {
        val r = NativeSignInResult.Error("No credentials available", IllegalStateException("28444"))
            .route(fallbackAvailable = true)
        assertTrue(r.fellBack, "a native Error must retry via the web flow")
        assertNull(r.error, "handing off must NOT also report an error — the flow is still live")
    }

    /**
     * The link-identity case. Linking attaches the provider to the CURRENT session while the web leg
     * signs in as a NEW one, so falling back there would replace a guest session — losing whatever
     * it holds — to avoid an error message. No fallback is passed, and the original report stands.
     */
    @Test
    fun a_native_error_still_reports_rejected_when_no_fallback_is_available() {
        val r = NativeSignInResult.Error("No credentials available", IllegalStateException("28444"))
            .route(fallbackAvailable = false)
        assertFalse(r.fellBack)
        val error = assertIs<KmpSupabaseAuthError.ProviderRejected>(r.error)
        assertTrue(error.provider == KmpSupabaseAuthProvider.GOOGLE)
    }

    @Test
    fun a_dismissal_is_never_retried_in_the_browser() {
        val r = NativeSignInResult.ClosedByUser.route(fallbackAvailable = true)
        assertFalse(r.fellBack, "someone who cancelled must not get a browser opened at them")
        assertIs<KmpSupabaseAuthError.Cancelled>(r.error)
    }

    @Test
    fun a_network_failure_is_never_retried_in_the_browser() {
        val r = NativeSignInResult.NetworkError("offline").route(fallbackAvailable = true)
        assertFalse(r.fellBack, "the web flow needs the same network the native path just failed on")
        assertIs<KmpSupabaseAuthError.Network>(r.error)
    }
}
