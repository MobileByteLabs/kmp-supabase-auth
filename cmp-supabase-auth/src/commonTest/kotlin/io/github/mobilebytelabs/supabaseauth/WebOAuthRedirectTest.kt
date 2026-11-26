package io.github.mobilebytelabs.supabaseauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebOAuthRedirectTest {

    // Android and iOS receive `myapp://…` through the app's own callback, so it must reach the provider.
    @Test
    fun appSchemeRedirect_isKept_whereTheAppCanReceiveIt() {
        assertEquals(
            "com.example.app://login-callback/paycraft",
            webOAuthRedirect("com.example.app://login-callback/paycraft", deliversAppScheme = true),
        )
    }

    // A browser or desktop JVM cannot open `myapp://…`: the provider would send the buyer to a dead link.
    // Null hands supabase-kt its own default — the current page on JS/Wasm, its local callback server on JVM.
    @Test
    fun appSchemeRedirect_isDropped_whereNothingCanReceiveIt() {
        assertNull(webOAuthRedirect("com.example.app://login-callback/paycraft", deliversAppScheme = false))
    }

    // An explicit https redirect (a web app's own page) is valid everywhere.
    @Test
    fun httpsRedirect_isKeptEverywhere() {
        assertEquals(
            "https://app.example.com/restore",
            webOAuthRedirect("https://app.example.com/restore", deliversAppScheme = false),
        )
    }

    @Test
    fun blankRedirect_isNull() {
        assertNull(webOAuthRedirect("  ", deliversAppScheme = true))
        assertNull(webOAuthRedirect(null, deliversAppScheme = false))
    }
}
