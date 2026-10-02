package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * In-app web OAuth via `ASWebAuthenticationSession` — the API Apple's own reviewers name.
 *
 * WHY THIS EXISTS: App Store Guideline 4 - Design rejection — "the user is taken to the default web
 * browser to sign in … implement the Safari View Controller API to display web content within the
 * app". supabase-kt opens `UIApplication.openURL` on iOS and ships no in-app option in any of its
 * three auth artifacts (measured, see the commonMain expect), so the flow is driven by hand here.
 *
 * `ASWebAuthenticationSession` is preferred over presenting `SFSafariViewController` directly:
 * it is SFSafariViewController-backed (so it satisfies the guideline), and it additionally OWNS the
 * callback — the system matches `callbackURLScheme` and hands the URL straight back, with no
 * app-wide URL-handler registration and no risk of another component consuming the redirect first.
 *
 * The flow is run manually rather than through `auth.signInWith` precisely because `signInWith`
 * would open the external browser. Instead: build the URL, present it, capture the callback,
 * exchange the code. `getOAuthUrl` and `exchangeCodeForSession` are both public API in 3.8.0 —
 * verified by compile probe before this was written.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun SupabaseClient.launchWebOAuth(
    provider: KmpSupabaseAuthProvider,
    redirectUrl: String?,
): Result<Unit> = runCatching {
    val oauthProvider = when (provider) {
        KmpSupabaseAuthProvider.GOOGLE -> Google
        KmpSupabaseAuthProvider.APPLE -> Apple
        else -> throw KmpSupabaseAuthError.ProviderRejected(provider)
    }
    // The scheme the system watches for. Derived from the redirect the caller configured, because
    // a mismatch here means the session never completes and the sheet just sits there.
    val scheme = redirectUrl?.substringBefore("://")?.takeIf { it.isNotBlank() && it != redirectUrl }
        ?: throw KmpSupabaseAuthError.ProviderRejected(provider)

    val url = auth.getOAuthUrl(oauthProvider, redirectUrl = redirectUrl)

    val callback: NSURL = suspendCancellableCoroutine { cont ->
        // Declared BEFORE the session: the completion closure captures it, and that capture is
        // what retains it. `presentationContextProvider` is a WEAK reference in Obj-C, so a
        // provider the session alone points at can be collected — after which the sheet silently
        // fails to present. A CLASS, not an `object`: Kotlin/Native refuses to allocate an
        // Obj-C-protocol-conforming singleton ("should have been lowered").
        val anchor = AnchorProvider()
        val session = ASWebAuthenticationSession(
            uRL = NSURL.URLWithString(url) ?: error("supabase returned an unparseable OAuth url"),
            callbackURLScheme = scheme,
            completionHandler = { callbackUrl, error ->
                anchor.hashCode() // retain: see the note above; without this the provider may die
                when {
                    // User-cancelled is a NORMAL outcome, not a failure to report as one — it is
                    // what tapping the sheet's Cancel produces.
                    error != null -> cont.resume(null)

                    else -> cont.resume(callbackUrl)
                }
            },
        )
        session.presentationContextProvider = anchor
        // false: reuse the system's cookie jar so an already-signed-in Apple user is not forced to
        // re-enter credentials. Ephemeral would be hostile here, not safer.
        session.prefersEphemeralWebBrowserSession = false
        cont.invokeOnCancellation { session.cancel() }
        if (!session.start()) cont.resume(null)
    } ?: throw KmpSupabaseAuthError.Cancelled

    val code = NSURLComponents(uRL = callback, resolvingAgainstBaseURL = false)
        .queryItems
        ?.firstNotNullOfOrNull { item ->
            @Suppress("UNCHECKED_CAST")
            val name = (item as? platform.Foundation.NSURLQueryItem)?.name
            if (name == "code") item.value else null
        }
        ?: throw KmpSupabaseAuthError.Cancelled

    auth.exchangeCodeForSession(code)
    Unit
}

/**
 * ASWebAuthenticationSession requires a presentation anchor on iOS 13+; without one it refuses to
 * start and `start()` returns false with no visible error.
 */
private class AnchorProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
