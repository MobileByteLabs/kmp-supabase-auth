package io.github.mobilebytelabs.supabaseauth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android: the first `onActivityResumed` after this starts suspending.
 *
 * Registered on the Application that `KmpSupabaseAuthInitProvider` captured at process start, so
 * nothing is required of the consumer. The callback is unregistered in every exit path, including
 * cancellation — leaking an ActivityLifecycleCallbacks would hold the Activity for the process
 * lifetime.
 *
 * A Custom Tab is a separate Activity in the app's own task, so dismissing it resumes the host
 * Activity and this fires. Credential Manager renders over the app and may not pause it at all;
 * when it does not, this simply never emits and the watchdog remains the net — which is why the
 * watchdog is kept rather than replaced.
 *
 * No Application (the provider never ran, e.g. a unit test) → suspend forever, never a false
 * cancellation.
 */
public actual suspend fun awaitAppForegroundReturn(): Unit = suspendCancellableCoroutine { cont ->
    val app: Application = CurrentActivity.application ?: return@suspendCancellableCoroutine
    val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (cont.isActive) cont.resume(Unit)
        }
        override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
        override fun onActivityStarted(a: Activity) = Unit
        override fun onActivityPaused(a: Activity) = Unit
        override fun onActivityStopped(a: Activity) = Unit
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
        override fun onActivityDestroyed(a: Activity) = Unit
    }
    cont.invokeOnCancellation { app.unregisterActivityLifecycleCallbacks(callbacks) }
    app.registerActivityLifecycleCallbacks(callbacks)
}
