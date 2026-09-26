package io.github.mobilebytelabs.supabaseauth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks

/**
 * Receives the OAuth redirect on Android and hands it to GoTrue, then finishes immediately.
 *
 * ## Why this exists
 *
 * supabase-kt's Android OAuth flow opens a Custom Tab / browser and expects the app to catch the
 * return URL and call `handleDeeplinks(intent)`. The documented way is to add an intent-filter to
 * your own launcher activity and wire that call by hand — which every app must remember to do,
 * and which a real shipped app in this workspace did NOT: it enabled the Apple button on Android
 * (no native Apple provider there, so the flow falls back to the redirect), opened a browser, and
 * had no intent-filter anywhere to come back to. The flow was unfinishable and the build was
 * silent about it.
 *
 * Shipping a translucent, no-UI activity here means the consuming app gets a working callback by
 * manifest merge, with no launcher-activity surgery and nothing to forget.
 *
 * ## Wiring
 *
 * The client must be resolvable when the redirect lands. Set [client] during DI graph
 * construction — `supabaseAuthNetwork(...)` does this for you on Android.
 *
 * Not used by native Google on Android: Credential Manager never leaves the app.
 */
public class SupabaseAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forward(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        forward(intent)
        finish()
    }

    private fun forward(intent: Intent?) {
        val supabase = client ?: return
        if (intent == null) return
        // Never throw out of a callback activity: a failed exchange must leave the app on its
        // previous screen with the session unchanged, not crash on return from a browser.
        runCatching { supabase.handleDeeplinks(intent) }
    }

    public companion object {
        /**
         * The client the redirect is delivered to.
         *
         * A static seam rather than DI lookup because the activity is constructed by the OS on
         * return from the browser, with no scope to inject from. Set once at graph construction.
         */
        @JvmStatic
        public var client: SupabaseClient? = null
    }
}
