package io.github.mobilebytelabs.supabaseauth

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.coroutines.resume

/**
 * iOS: the first `UIApplicationDidBecomeActive` after this starts suspending.
 *
 * `ASWebAuthenticationSession` normally delivers its own completion on dismiss (NSError code 1,
 * `canceledLogin`), so this is a SECOND net rather than the primary one — it covers the case where
 * the sheet goes away without the completion firing, which is the shape the Android measurement
 * showed and which no API guarantees against here either.
 *
 * The observer is removed on every exit path including cancellation; a retained observer on a
 * dead continuation would resume it later and report a cancellation for a flow that already ended.
 */
public actual suspend fun awaitAppForegroundReturn(): Unit = suspendCancellableCoroutine { cont ->
    val center = NSNotificationCenter.defaultCenter
    val observer = center.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = null,
    ) { _ -> if (cont.isActive) cont.resume(Unit) }
    cont.invokeOnCancellation { center.removeObserver(observer) }
}
