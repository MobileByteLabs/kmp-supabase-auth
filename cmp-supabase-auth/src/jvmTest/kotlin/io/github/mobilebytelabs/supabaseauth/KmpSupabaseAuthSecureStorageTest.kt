package io.github.mobilebytelabs.supabaseauth

import com.russhwolf.settings.Settings
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.mobilebytelabs.supabaseauth.di.kmpSupabaseAuthExtras
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `secureStorage(settings)` must route BOTH the session and the PKCE code verifier into the app's
 * store — the whole point is that the refresh token never reaches supabase-kt's plain default.
 */
class KmpSupabaseAuthSecureStorageTest {

    // Nothing here should reach the network; any request that does fails rather than leaves the machine.
    private val offline = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }

    private val config = KmpSupabaseAuthConfig(projectRef = "abcdefgh", redirectUrl = "app://login-callback")

    @Test
    fun sessionAndCodeVerifierLandInTheSuppliedStore() = runTest {
        val store = RecordingSettings()
        val client = createSupabaseClient("https://abcdefgh.supabase.co", "sb_publishable_test") {
            httpEngine = offline
            kmpSupabaseAuthExtras(config) { secureStorage(store) }(this)
        }
        val auth = client.auth

        auth.config.sessionManager!!.saveSession(
            UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer"),
        )
        assertTrue(store.values.values.any { it.contains("refresh") }, "session stored in the supplied settings")

        auth.config.codeVerifierCache!!.saveCodeVerifier("verifier")
        assertEquals("verifier", auth.config.codeVerifierCache!!.loadCodeVerifier())
        assertTrue(store.values.values.any { it == "verifier" }, "code verifier stored in the supplied settings")
    }

    @Test
    fun theRedirectIsKeptWhenSecureStorageIsSet() {
        // secureStorage is applied INSIDE the library's install(Auth), so it must not cost the redirect.
        val client = createSupabaseClient("https://abcdefgh.supabase.co", "sb_publishable_test") {
            httpEngine = offline
            kmpSupabaseAuthExtras(config) { secureStorage(RecordingSettings()) }(this)
        }
        assertEquals("app", client.auth.config.scheme)
        assertEquals("login-callback", client.auth.config.host)
    }
}

/** Minimal in-memory [Settings]; only strings are exercised, the rest delegate to the same map. */
private class RecordingSettings : Settings {
    val values = mutableMapOf<String, String>()
    override val keys: Set<String> get() = values.keys
    override val size: Int get() = values.size
    override fun clear() = values.clear()
    override fun remove(key: String) {
        values.remove(key)
    }
    override fun hasKey(key: String) = key in values
    override fun putString(key: String, value: String) {
        values[key] = value
    }
    override fun getString(key: String, defaultValue: String) = values[key] ?: defaultValue
    override fun getStringOrNull(key: String) = values[key]
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getInt(key: String, defaultValue: Int) = getIntOrNull(key) ?: defaultValue
    override fun getIntOrNull(key: String) = values[key]?.toIntOrNull()
    override fun putLong(key: String, value: Long) = putString(key, value.toString())
    override fun getLong(key: String, defaultValue: Long) = getLongOrNull(key) ?: defaultValue
    override fun getLongOrNull(key: String) = values[key]?.toLongOrNull()
    override fun putFloat(key: String, value: Float) = putString(key, value.toString())
    override fun getFloat(key: String, defaultValue: Float) = getFloatOrNull(key) ?: defaultValue
    override fun getFloatOrNull(key: String) = values[key]?.toFloatOrNull()
    override fun putDouble(key: String, value: Double) = putString(key, value.toString())
    override fun getDouble(key: String, defaultValue: Double) = getDoubleOrNull(key) ?: defaultValue
    override fun getDoubleOrNull(key: String) = values[key]?.toDoubleOrNull()
    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getBoolean(key: String, defaultValue: Boolean) = getBooleanOrNull(key) ?: defaultValue
    override fun getBooleanOrNull(key: String) = values[key]?.toBooleanStrictOrNull()
}
