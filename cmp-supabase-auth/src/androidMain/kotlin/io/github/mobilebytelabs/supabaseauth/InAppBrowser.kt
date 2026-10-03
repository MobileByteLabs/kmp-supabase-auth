package io.github.mobilebytelabs.supabaseauth

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.auth.UrlLauncher

/**
 * Keep the OAuth tab INSIDE the app's own task.
 *
 * Setting `ExternalAuthAction.CustomTabs` alone is NOT enough, and that is the subtle part.
 * supabase-kt's own launcher calls `CustomTabsIntent.launchUrl(applicationContext, uri)` — from the
 * APPLICATION context, which forces `FLAG_ACTIVITY_NEW_TASK`. The result is a real Custom Tab that
 * nonetheless lands in Chrome's OWN task and shows up as a SECOND card in recents.
 *
 * MEASURED on-device 2026-10-02, which is the only reason this was caught — the activity class said
 * `customtabs.CustomTabActivity`, which looks correct, while the task dump said otherwise:
 *
 *     Task #1673  A=…:com.mobilebytesensei.cappy   ← the app
 *     Task #1675  A=…:com.android.chrome           ← the tab, separate task
 *     recents: #0 cappy   #1 com.android.chrome
 *
 * To the user that IS "opening in a separate Chrome tab", and reporting the activity name as proof
 * was measuring the wrong thing. `intentModifier` cannot fix it either: clearing NEW_TASK while
 * launching from the application context throws.
 *
 * So the launch itself is replaced via `AuthConfig.urlLauncher`, using the CURRENT ACTIVITY as the
 * context. Launched from an Activity without NEW_TASK, the tab joins that activity's task: one
 * recents card, back returns to the app.
 */
@OptIn(SupabaseExperimental::class)
internal actual fun AuthConfig.preferInAppBrowser() {
    urlLauncher = ActivityScopedTabLauncher
}

@OptIn(SupabaseExperimental::class)
private object ActivityScopedTabLauncher : UrlLauncher {
    override suspend fun openUrl(client: SupabaseClient, url: String) {
        val activity = CurrentActivity.get()
        val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
        if (activity != null) {
            // No NEW_TASK: the tab becomes part of THIS activity's task.
            tab.launchUrl(activity, Uri.parse(url))
        } else {
            // Degrade to supabase-kt's own behaviour rather than crash. Reaching here means no
            // activity was resumed when sign-in started, which should not happen from a button tap.
            val app = CurrentActivity.application ?: return
            tab.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            tab.launchUrl(app, Uri.parse(url))
        }
    }
}

/**
 * The resumed Activity, tracked so the launcher has a task to join.
 *
 * Registered lazily off supabase-kt's own `applicationContext()` (public API) rather than requiring
 * the consumer to pass an Application in — this library's whole public surface is commonMain, and a
 * platform-specific init parameter would break that.
 */
internal object CurrentActivity : Application.ActivityLifecycleCallbacks {
    private var ref: java.lang.ref.WeakReference<Activity>? = null
    internal var application: Application? = null
        private set

    fun get(): Activity? = ref?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    /**
     * Called once from [KmpSupabaseAuthInitProvider] at process start. supabase-kt's own
     * `applicationContext()` is Kotlin-`internal` (public only in the bytecode), so it cannot be
     * called from here — hence the library captures its own Application rather than asking the
     * consumer for one, which would break the commonMain-only public surface.
     */
    fun install(context: Context) {
        if (application != null) return
        val app = context.applicationContext as? Application ?: return
        application = app
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        ref = java.lang.ref.WeakReference(activity)
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }
}
