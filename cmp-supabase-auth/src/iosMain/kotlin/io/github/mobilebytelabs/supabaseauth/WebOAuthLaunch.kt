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
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.create
import platform.Foundation.stringByRemovingPercentEncoding
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

    // Stage marker. If this line appears and "callback received" never does, the sheet was
    // presented and the system never matched the callback scheme — which is a redirect/scheme
    // problem, NOT a parsing one. Host+path only: the OAuth url carries query parameters.
    KmpSupabaseAuthLog.log {
        val u = NSURL.URLWithString(url)
        "$provider: presenting ASWebAuthenticationSession — callbackScheme=$scheme " +
            "authorize=${u?.host}${u?.path}"
    }

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
                    // what tapping the sheet's Cancel produces (domain
                    // ASWebAuthenticationSessionErrorDomain, code 1 = canceledLogin). ANY OTHER
                    // error is a real fault that must be visible: code 2 is
                    // presentationContextNotProvided / presentationContextInvalid, which presents
                    // nothing and used to be indistinguishable from the user tapping Cancel.
                    error != null -> {
                        KmpSupabaseAuthLog.log {
                            "$provider: session ended with error — domain=${error.domain} " +
                                "code=${error.code} (1=user-cancelled, 2=presentation-context)"
                        }
                        cont.resume(null)
                    }

                    else -> {
                        KmpSupabaseAuthLog.log { "$provider: session returned a callback url" }
                        cont.resume(callbackUrl)
                    }
                }
            },
        )
        session.presentationContextProvider = anchor
        // false: reuse the system's cookie jar so an already-signed-in Apple user is not forced to
        // re-enter credentials. Ephemeral would be hostile here, not safer.
        session.prefersEphemeralWebBrowserSession = false
        cont.invokeOnCancellation { session.cancel() }
        if (!session.start()) {
            // start() returning false means the sheet NEVER APPEARED — a missing presentation
            // anchor is the usual cause. Silent until now, and reported as Cancelled.
            KmpSupabaseAuthLog.log { "$provider: session.start() returned FALSE — sheet not presented" }
            cont.resume(null)
        }
    } ?: throw KmpSupabaseAuthError.Cancelled

    // The session arrives in ONE of two shapes, decided by `AuthConfig.flowType`:
    //
    //   IMPLICIT (supabase-kt's DEFAULT — `AuthConfigDefaults.flowType = FlowType.IMPLICIT`,
    //             confirmed from 3.8.0 bytecode) → tokens in the URL **FRAGMENT**:
    //             `myapp://login-callback#access_token=…&refresh_token=…&token_type=bearer`
    //   PKCE                                     → a code in the **QUERY**:
    //             `myapp://login-callback?code=…`
    //
    // Reading only the query for `code` is why this silently discarded every successful sign-in:
    // the callback arrived, carried no `code` (because the default flow puts tokens in the
    // fragment), and the miss was reported as `Cancelled` — indistinguishable from the user
    // dismissing the sheet. MEASURED on mbs/cappy 2026-10-02: Google authenticated, Safari
    // returned to the app, and nothing happened.
    //
    // Both are handled rather than pinning a flowType, because the flow is the CONSUMER's choice
    // (they may set PKCE on their own client) and this must not break when they change it.
    val components = NSURLComponents(uRL = callback, resolvingAgainstBaseURL = false)
    val query = components.query.parseUrlEncodedParams()
    val fragment = components.fragment.parseUrlEncodedParams()

    // Names only — a value here is an access token or an id token. Never log one.
    KmpSupabaseAuthLog.log {
        "$provider: callback received — query=[${query.keys.sorted().joinToString()}] " +
            "fragment=[${fragment.keys.sorted().joinToString()}]"
    }

    // A provider that REFUSES reports it in whichever half it used. Treating that as `Cancelled`
    // (the old behaviour for anything unparseable) hides a real, actionable failure.
    val providerError = query["error_description"] ?: query["error"]
        ?: fragment["error_description"] ?: fragment["error"]
    if (providerError != null) {
        KmpSupabaseAuthLog.log { "$provider: callback carried an error — $providerError" }
        throw KmpSupabaseAuthError.ProviderRejected(provider, IllegalStateException(providerError))
    }

    val code = query["code"]
    val accessToken = fragment["access_token"]
    val refreshToken = fragment["refresh_token"]

    when {
        code != null -> {
            KmpSupabaseAuthLog.log { "$provider: PKCE callback — exchanging code for a session" }
            auth.exchangeCodeForSession(code)
        }

        accessToken != null && refreshToken != null -> {
            KmpSupabaseAuthLog.log { "$provider: IMPLICIT callback — importing the returned session" }
            // retrieveUser = true: the implicit callback returns TOKENS only, no user object. The
            // session would be Authenticated with a null user, so anything mapping the profile
            // (`currentUser`, a consumer's AuthUser -> domain mapping) would see blanks after a
            // perfectly successful sign-in. One extra GET /user is worth that.
            auth.importAuthToken(
                accessToken = accessToken,
                refreshToken = refreshToken,
                retrieveUser = true,
            )
        }

        // Reached only if the callback is genuinely malformed. Distinct from Cancelled on purpose:
        // conflating the two is exactly what made this bug invisible for a whole device session.
        else -> throw KmpSupabaseAuthError.ProviderRejected(
            provider,
            IllegalStateException(
                "OAuth callback carried neither a PKCE `code` (query) nor implicit " +
                    "`access_token`+`refresh_token` (fragment)",
            ),
        )
    }
    Unit
}

/**
 * Splits a `a=1&b=2` query or fragment into a map, percent-decoding each value.
 *
 * Tokens are URL-safe base64 and need no decoding, but `error_description` routinely arrives
 * percent-encoded and is read by a human in a log. Splits on the FIRST `=` only — a base64 value
 * can legitimately end in `=` padding, and splitting on every one truncates it.
 */
private fun String?.parseUrlEncodedParams(): Map<String, String> = this?.split("&")
    ?.mapNotNull { pair ->
        val i = pair.indexOf('=')
        if (i <= 0) {
            null
        } else {
            val value = pair.substring(i + 1)
            val decoded = NSString.create(string = value).stringByRemovingPercentEncoding ?: value
            pair.substring(0, i) to decoded
        }
    }
    ?.toMap()
    .orEmpty()

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
