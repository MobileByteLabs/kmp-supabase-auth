package io.github.mobilebytelabs.supabaseauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupabaseAuthConfigTest {

    @Test
    fun hasGoogleNative_isTrue_whenWebClientIdPresent() {
        val config = SupabaseAuthConfig(
            projectRef = "abcdefgh",
            googleWebClientId = "123.apps.googleusercontent.com",
        )
        assertTrue(config.hasGoogleNative)
    }

    // An app mid-way through Google console setup must still build and run. Blank has to degrade
    // to the OAuth-redirect path, never throw and never half-configure the native flow.
    @Test
    fun hasGoogleNative_isFalse_whenWebClientIdBlankOrWhitespace() {
        assertFalse(SupabaseAuthConfig("abcdefgh", googleWebClientId = "").hasGoogleNative)
        assertFalse(SupabaseAuthConfig("abcdefgh", googleWebClientId = "   ").hasGoogleNative)
        assertFalse(SupabaseAuthConfig("abcdefgh").hasGoogleNative)
    }

    @Test
    fun redirectUrl_splitsIntoSchemeAndHost() {
        val config = SupabaseAuthConfig("abcdefgh", redirectUrl = "myapp://login-callback")
        assertEquals("myapp", config.oauthScheme)
        assertEquals("login-callback", config.oauthHost)
    }

    @Test
    fun redirectUrl_withoutSeparator_yieldsNulls() {
        val config = SupabaseAuthConfig("abcdefgh", redirectUrl = "not-a-url")
        assertNull(config.oauthScheme)
        assertNull(config.oauthHost)
    }

    @Test
    fun redirectUrl_blank_yieldsNulls() {
        val config = SupabaseAuthConfig("abcdefgh")
        assertNull(config.oauthScheme)
        assertNull(config.oauthHost)
    }

    // Placeholder SHAPE, not just emptiness — an unfilled template must not reach a release build.
    @Test
    fun isConfigured_rejectsBlankAndPlaceholderProjectRefs() {
        assertTrue(SupabaseAuthConfig("abcdefgh").isConfigured)
        assertFalse(SupabaseAuthConfig("").isConfigured)
        assertFalse(SupabaseAuthConfig("   ").isConfigured)
        assertFalse(SupabaseAuthConfig("YOUR_PROJECT_REF").isConfigured)
        assertFalse(SupabaseAuthConfig("your_project_ref").isConfigured)
    }

    @Test
    fun validate_returnsConfigUnchanged_whenUsable() {
        val config = SupabaseAuthConfig("abcdefgh", googleWebClientId = "id")
        assertEquals(config, SupabaseAuth.validate(config))
    }

    // A missing provider id is recoverable (OAuth redirect); an absent projectRef is not —
    // there is no client to install onto.
    @Test
    fun validate_throws_onPlaceholderProjectRef() {
        assertFailsWith<IllegalArgumentException> {
            SupabaseAuth.validate(SupabaseAuthConfig("YOUR_PROJECT_REF"))
        }
    }

    @Test
    fun validate_doesNotThrow_whenOnlyProvidersAreUnconfigured() {
        val bare = SupabaseAuthConfig("abcdefgh")
        assertEquals(bare, SupabaseAuth.validate(bare))
    }
}
