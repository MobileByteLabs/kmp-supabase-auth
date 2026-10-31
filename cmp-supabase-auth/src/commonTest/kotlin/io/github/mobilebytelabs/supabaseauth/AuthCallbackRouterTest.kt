package io.github.mobilebytelabs.supabaseauth

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Android OAuth callback must deliver each redirect to the client that STARTED the flow.
 *
 * It held ONE client. An SDK with its own Supabase project (PayCraft) registering there would have
 * taken the host app's redirect — the app's own sign-in would land in the SDK's project. Routing by
 * the redirect PATH keeps both: the app keeps `<appId>://login-callback`, the SDK uses
 * `<appId>://login-callback/<sdk>`. The manifest filter matches on host, so no manifest change.
 */
class AuthCallbackRouterTest {

    private val app = "app-client"
    private val sdk = "paycraft-client"

    @AfterTest
    fun reset() = AuthCallbackRouter.clearForTest()

    @Test
    fun a_redirect_with_the_sdk_path_goes_to_the_sdk_client() {
        AuthCallbackRouter.register(app, "com.example://login-callback")
        AuthCallbackRouter.register(sdk, "com.example://login-callback/paycraft")

        assertEquals(sdk, AuthCallbackRouter.resolve("/paycraft"))
    }

    @Test
    fun the_app_redirect_still_goes_to_the_app_client() {
        AuthCallbackRouter.register(app, "com.example://login-callback")
        AuthCallbackRouter.register(sdk, "com.example://login-callback/paycraft")

        assertEquals(app, AuthCallbackRouter.resolve(null))
        assertEquals(app, AuthCallbackRouter.resolve(""))
        assertEquals(app, AuthCallbackRouter.resolve("/"))
    }

    @Test
    fun an_unknown_path_falls_back_to_the_app_client_never_to_the_sdk() {
        AuthCallbackRouter.register(sdk, "com.example://login-callback/paycraft")
        AuthCallbackRouter.register(app, null) // the library's own DI registers with no path

        assertEquals(app, AuthCallbackRouter.resolve("/something-else"))
    }

    @Test
    fun nothing_registered_resolves_to_nothing() {
        assertNull(AuthCallbackRouter.resolve("/paycraft"))
    }
}
