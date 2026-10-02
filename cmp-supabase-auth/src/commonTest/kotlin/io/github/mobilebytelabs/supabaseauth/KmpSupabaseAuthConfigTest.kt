package io.github.mobilebytelabs.supabaseauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmpSupabaseAuthConfigTest {

    // A configured web client id enables native Google only where the PLATFORM can deliver it.
    // Asserting `true` unconditionally would fail on iOS — correctly, since iOS has no native path
    // this library can ship (the GoogleSignIn SDK is outside a Maven artifact's reach). Expressing
    // the contract against `googleNativeSupported` is what makes this test true on all 17 targets
    // instead of only the ones that happen to agree with it.
    @Test
    fun hasGoogleNative_followsPlatformCapability_whenWebClientIdPresent() {
        val config = KmpSupabaseAuthConfig(
            projectRef = "abcdefgh",
            googleWebClientId = "123.apps.googleusercontent.com",
        )
        assertEquals(googleNativeSupported, config.hasGoogleNative)
    }

    // The delivery half alone is never enough — an unconfigured client id still degrades to web.
    @Test
    fun hasGoogleNative_isFalse_onPlatformsWithoutANativePath() {
        if (googleNativeSupported) return
        assertFalse(
            KmpSupabaseAuthConfig("abcdefgh", googleWebClientId = "123.apps.googleusercontent.com")
                .hasGoogleNative,
        )
    }

    // An app mid-way through Google console setup must still build and run. Blank has to degrade
    // to the OAuth-redirect path, never throw and never half-configure the native flow.
    @Test
    fun hasGoogleNative_isFalse_whenWebClientIdBlankOrWhitespace() {
        assertFalse(KmpSupabaseAuthConfig("abcdefgh", googleWebClientId = "").hasGoogleNative)
        assertFalse(KmpSupabaseAuthConfig("abcdefgh", googleWebClientId = "   ").hasGoogleNative)
        assertFalse(KmpSupabaseAuthConfig("abcdefgh").hasGoogleNative)
    }

    @Test
    fun redirectUrl_splitsIntoSchemeAndHost() {
        val config = KmpSupabaseAuthConfig("abcdefgh", redirectUrl = "myapp://login-callback")
        assertEquals("myapp", config.oauthScheme)
        assertEquals("login-callback", config.oauthHost)
    }

    @Test
    fun redirectUrl_withoutSeparator_yieldsNulls() {
        val config = KmpSupabaseAuthConfig("abcdefgh", redirectUrl = "not-a-url")
        assertNull(config.oauthScheme)
        assertNull(config.oauthHost)
    }

    @Test
    fun redirectUrl_blank_yieldsNulls() {
        val config = KmpSupabaseAuthConfig("abcdefgh")
        assertNull(config.oauthScheme)
        assertNull(config.oauthHost)
    }

    // Placeholder SHAPE, not just emptiness — an unfilled template must not reach a release build.
    @Test
    fun isConfigured_rejectsBlankAndPlaceholderProjectRefs() {
        assertTrue(KmpSupabaseAuthConfig("abcdefgh").isConfigured)
        assertFalse(KmpSupabaseAuthConfig("").isConfigured)
        assertFalse(KmpSupabaseAuthConfig("   ").isConfigured)
        assertFalse(KmpSupabaseAuthConfig("YOUR_PROJECT_REF").isConfigured)
        assertFalse(KmpSupabaseAuthConfig("your_project_ref").isConfigured)
    }

    @Test
    fun validate_returnsConfigUnchanged_whenUsable() {
        val config = KmpSupabaseAuthConfig("abcdefgh", googleWebClientId = "id")
        assertEquals(config, KmpSupabaseAuth.validate(config))
    }

    // A missing provider id is recoverable (OAuth redirect); an absent projectRef is not —
    // there is no client to install onto.
    @Test
    fun validate_throws_onPlaceholderProjectRef() {
        assertFailsWith<IllegalArgumentException> {
            KmpSupabaseAuth.validate(KmpSupabaseAuthConfig("YOUR_PROJECT_REF"))
        }
    }

    @Test
    fun validate_doesNotThrow_whenOnlyProvidersAreUnconfigured() {
        val bare = KmpSupabaseAuthConfig("abcdefgh")
        assertEquals(bare, KmpSupabaseAuth.validate(bare))
    }
}
