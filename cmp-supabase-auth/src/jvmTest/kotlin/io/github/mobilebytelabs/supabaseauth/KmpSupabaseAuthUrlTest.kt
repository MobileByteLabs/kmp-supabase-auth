package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.mobilebytelabs.supabaseauth.di.kmpSupabaseAuthExtras
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `authUrl(url)` sends every Auth (GoTrue) call to a proxy in front of the project — e.g. a Worker
 * that adds the project key server-side, so the app ships none. Without it sign-in hits the project
 * directly, and an app built with no key fails 401 "No API key found in request".
 */
class KmpSupabaseAuthUrlTest {

    // Nothing here reaches the network; a request that did would fail rather than leave the machine.
    private val offline = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }

    private val config = KmpSupabaseAuthConfig(projectRef = "abcdefgh", redirectUrl = "app://login-callback")

    @Test
    fun authCallsGoToTheProxy() {
        val client = createSupabaseClient("https://abcdefgh.supabase.co", "sb_publishable_test") {
            httpEngine = offline
            kmpSupabaseAuthExtras(config) { authUrl("https://api.example.com/auth/v1") }(this)
        }
        assertEquals("https://api.example.com/auth/v1/token", client.auth.resolveUrl("token"))
    }

    @Test
    fun withoutItAuthStaysOnTheProject() {
        val client = createSupabaseClient("https://abcdefgh.supabase.co", "sb_publishable_test") {
            httpEngine = offline
            kmpSupabaseAuthExtras(config)(this)
        }
        assertTrue(client.auth.resolveUrl("token").startsWith("https://abcdefgh.supabase.co/auth/v1/"))
    }

    @Test
    fun composesWithSecureStorageInEitherOrder() {
        for (secureFirst in listOf(true, false)) {
            val client = createSupabaseClient("https://abcdefgh.supabase.co", "sb_publishable_test") {
                httpEngine = offline
                kmpSupabaseAuthExtras(config) {
                    if (secureFirst) secureStorage(RecordingSettings())
                    authUrl("https://api.example.com/auth/v1")
                    if (!secureFirst) secureStorage(RecordingSettings())
                }(this)
            }
            assertEquals("https://api.example.com/auth/v1/user", client.auth.resolveUrl("user"))
            assertEquals("login-callback", client.auth.config.host, "redirect kept")
        }
    }
}
