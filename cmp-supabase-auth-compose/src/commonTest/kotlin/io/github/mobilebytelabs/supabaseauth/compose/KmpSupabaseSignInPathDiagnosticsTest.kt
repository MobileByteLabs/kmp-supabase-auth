package io.github.mobilebytelabs.supabaseauth.compose

import io.github.jan.supabase.PlatformTarget
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun config(googleWebClientId: String = "123.apps.googleusercontent.com") = KmpSupabaseAuthConfig(
    projectRef = "abcdefgh",
    googleWebClientId = googleWebClientId,
    redirectUrl = "myapp://login-callback",
)

class KmpSupabaseSignInPathDiagnosticsTest {

    @Test
    fun ios_fully_configured_reports_both_native() {
        val r = diagnoseKmpSupabaseSignInPaths(config(), platform = PlatformTarget.IOS)
        assertEquals(KmpSupabaseSignInPath.NATIVE, r.google.path)
        assertEquals(KmpSupabaseSignInPath.NATIVE, r.apple.path)
        assertTrue(r.webFallbacks.isEmpty(), "nothing should fall back to a browser")
    }

    /**
     * The App Review case: Apple stays native so the app looks correct, while Google silently
     * degrades to the external Safari flow. This is the whole reason the diagnostic exists, so
     * it is asserted explicitly rather than inferred from the matrix test below.
     */
    @Test
    fun ios_with_blank_google_client_id_degrades_google_to_web_but_keeps_apple_native() {
        val r = diagnoseKmpSupabaseSignInPaths(config(googleWebClientId = ""), platform = PlatformTarget.IOS)
        assertEquals(KmpSupabaseSignInPath.WEB_FALLBACK, r.google.path)
        assertEquals(KmpSupabaseSignInPath.NATIVE, r.apple.path)
        assertEquals(listOf(KmpSupabaseAuthProvider.GOOGLE), r.webFallbacks.map { it.provider })
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
