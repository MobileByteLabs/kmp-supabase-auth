package io.github.mobilebytelabs.supabaseauth

/**
 * Which client an OAuth redirect belongs to, keyed by the redirect URL's PATH.
 *
 * The host app's own sign-in registers with no path (`<appId>://login-callback`) and is the
 * fallback; an SDK that owns its own Supabase project registers a distinct path
 * (`<appId>://login-callback/paycraft`) via [kmpSupabaseAuthClientFor]. One callback activity, one
 * manifest filter (it matches on host), every client gets its own redirects.
 *
 * Holds `Any` so it is testable without a live SupabaseClient; the Android activity narrows it.
 */
// ponytail: unsynchronized map — registration happens once at graph build, before any redirect.
internal object AuthCallbackRouter {
    private val clients = mutableMapOf<String, Any>()

    fun register(client: Any, redirectUrl: String?) {
        clients[pathOf(redirectUrl)] = client
    }

    /** The client for [path] (an Intent's `data.path`), else the app's default, else null. */
    fun resolve(path: String?): Any? = clients[normalize(path)] ?: clients[""]

    fun clearForTest() = clients.clear()

    /** `scheme://host/a/b?x` → `a/b`; no path → "". */
    private fun pathOf(url: String?): String =
        normalize(url?.substringAfter("://", "")?.substringAfter('/', "")?.substringBefore('?'))

    private fun normalize(path: String?): String = path.orEmpty().trim('/')
}
