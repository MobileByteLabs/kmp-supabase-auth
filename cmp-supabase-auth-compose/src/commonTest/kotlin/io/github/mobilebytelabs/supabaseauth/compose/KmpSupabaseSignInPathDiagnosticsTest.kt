package io.github.mobilebytelabs.supabaseauth.compose

import io.github.jan.supabase.PlatformTarget
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthConfig
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private fun config(googleWebClientId: String = "123.apps.googleusercontent.com") = KmpSupabaseAuthConfig(
    projectRef = "abcdefgh",
    googleWebClientId = googleWebClientId,
    redirectUrl = "myapp://login-callback",
)

class KmpSupabaseSignInPathDiagnosticsTest {

    @Test
    fun ios_reports_apple_native_and_google_web_even_when_fully_configured() {
        val r = diagnoseKmpSupabaseSignInPaths(config(), platform = PlatformTarget.IOS)
        // Google is WEB on iOS no matter how well configured: the native path needs the GoogleSignIn
        // SDK in the app's own Xcode/SPM graph, which Xcode resolves before Gradle runs, so no
        // published library can deliver it. This test asserted NATIVE until 2026-10-02 and was
        // encoding a path that aborts at the first tap on a real device.
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.google.path)
        // Apple IS native here — `appleNativeLogin` resolves through AuthenticationServices, a
        // SYSTEM framework. That asymmetry is the whole point: on a real iPhone 13, Apple sign-in
        // completed while Google aborted.
        assertEquals(KmpSupabaseSignInPath.NATIVE, r.apple.path)
        assertEquals(listOf(KmpSupabaseAuthProvider.GOOGLE), r.webFallbacks.map { it.provider })
    }

    /**
     * On iOS the REASON must name the platform, not the client id — even when the id really is
     * blank. Reporting "googleWebClientId is blank" there sends someone to fill in a field that
     * cannot change the outcome; the diagnostic exists to prevent exactly that wasted trip.
     */
    @Test
    fun ios_blank_google_client_id_still_reports_the_platform_as_the_reason() {
        val r = diagnoseKmpSupabaseSignInPaths(config(googleWebClientId = ""), platform = PlatformTarget.IOS)
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.google.path)
        assertEquals(KmpSupabaseSignInPath.NATIVE, r.apple.path)
        assertEquals(listOf(KmpSupabaseAuthProvider.GOOGLE), r.webFallbacks.map { it.provider })
        assertContains(r.google.reason, "no native Google path")
    }

    /**
     * The blank-client-id reason is still reported where it IS the cause — Android, whose native
     * path is real and genuinely blocked by a missing id. Moving the iOS assertion above must not
     * cost coverage of this branch.
     */
    @Test
    fun android_blank_google_client_id_reports_the_client_id_as_the_reason() {
        val r = diagnoseKmpSupabaseSignInPaths(config(googleWebClientId = ""), platform = PlatformTarget.ANDROID)
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.google.path)
        assertContains(r.google.reason, "googleWebClientId is blank")
    }

    @Test
    fun blank_is_detected_for_whitespace_too() {
        val r = diagnoseKmpSupabaseSignInPaths(config(googleWebClientId = "   "), platform = PlatformTarget.IOS)
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.google.path)
    }

    @Test
    fun android_has_native_google_but_never_native_apple() {
        val r = diagnoseKmpSupabaseSignInPaths(config(), platform = PlatformTarget.ANDROID)
        assertEquals(KmpSupabaseSignInPath.NATIVE, r.google.path)
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.apple.path)
        assertContains(r.apple.reason, "no native sign-in SDK outside iOS")
    }

    @Test
    fun platforms_without_native_support_report_web_for_both() {
        listOf(PlatformTarget.JVM, PlatformTarget.JS, PlatformTarget.WASM_JS).forEach { p ->
            val r = diagnoseKmpSupabaseSignInPaths(config(), platform = p)
            assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.google.path, "google on $p")
            assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.apple.path, "apple on $p")
        }
    }

    @Test
    fun opting_out_is_reported_as_the_reason() {
        val r = diagnoseKmpSupabaseSignInPaths(
            config(),
            googleNative = false,
            appleNative = false,
            platform = PlatformTarget.IOS,
        )
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.google.path)
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.apple.path)
        assertContains(r.google.reason, "googleNative = false")
        assertContains(r.apple.reason, "appleNative = false")
    }

    @Test
    fun format_names_every_provider_and_the_platform() {
        val text = diagnoseKmpSupabaseSignInPaths(config(), platform = PlatformTarget.IOS).format()
        assertContains(text, "IOS")
        assertContains(text, "GOOGLE")
        assertContains(text, "APPLE")
    }
}
