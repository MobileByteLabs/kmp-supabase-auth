package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.composable.GoogleDialogType
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithApple
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthClient
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthLog
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.awaitAppForegroundReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long a launcher waits for a provider before reporting [KmpSupabaseAuthError.NoResponse].
 *
 * Generous on purpose: a real provider sheet can sit open while someone finds their password, and
 * timing that out would be worse than the bug this guards against.
 */
public val KmpSupabaseDefaultSignInTimeout: Duration = 60.seconds

/**
 * Native Google sign-in — Credential Manager on Android, native on iOS, GoTrue OAuth redirect
 * everywhere else (the plugin chooses).
 *
 * [onError] reports only genuine failures. **Success is deliberately NOT reported here.** On
 * Android the `NativeSignInResult.Success` callback frequently never fires even though the
 * id_token exchange succeeded and the session landed, so a UI waiting on it hangs on a spinner
 * while the person is already signed in. Observe `KmpSupabaseAuthRepository.session` instead.
 *
 * Pass [linkIdentity] to attach this provider to the CURRENT session rather than starting a new
 * one — the guest-to-account upgrade, implemented via `ComposeAuth.LINK_IDENTITY_CALLBACK`.
 *
 * @param timeout report [KmpSupabaseAuthError.NoResponse] if the provider never calls back at all. Pass
 *   [Duration.INFINITE] to disable. See [rememberSignInWithWatchdog] for why this exists.
 * @param dialogType how Credential Manager presents the account chooser on Android.
 *   **Defaults to [GoogleDialogType.BOTTOM_SHEET]**, the modern "Sign in with Google" sheet.
 *   This default is not cosmetic: the full-screen DIALOG variant can show nothing at all and never
 *   call back when no credential matches, which presents as a sign-in that hangs forever with no
 *   error. A no-op on iOS/desktop/web, which use the GoTrue OAuth redirect.
 */
@Composable
public fun rememberKmpSupabaseGoogleSignIn(
    client: KmpSupabaseAuthClient = koinInject(),
    linkIdentity: Boolean = false,
    timeout: Duration = KmpSupabaseDefaultSignInTimeout,
    watchdogScope: CoroutineScope? = null,
    dialogType: GoogleDialogType = GoogleDialogType.BOTTOM_SHEET,
    onError: (KmpSupabaseAuthError) -> Unit = {},
): KmpSupabaseSignInLauncher {
    val raw = client.raw ?: return remember {
        KmpSupabaseAuthLog.log { "GOOGLE: client not configured — launcher will report NotConfigured" }
        KmpSupabaseSignInLauncher { onError(KmpSupabaseAuthError.NotConfigured) }
    }
    // Platforms with no library-deliverable native path go through the in-app web flow instead,
    // WITHOUT touching ComposeAuth. That matters: `rememberSignInWithGoogle` would fall back to
    // supabase-kt's own iOS redirect, which is `UIApplication.openURL` — the EXTERNAL Safari app
    // Apple rejected under Guideline 4. See KmpSupabaseAuthClient.supportsNativeGoogle.
    if (!client.supportsNativeGoogle) {
        if (linkIdentity) {
            KmpSupabaseAuthLog.log {
                "GOOGLE: linkIdentity requested but this platform uses the web flow, which signs in " +
                    "as a NEW session rather than linking to the current one — the request is ignored."
            }
        }
        return rememberWebOAuthSignIn(KmpSupabaseAuthProvider.GOOGLE, client, watchdogScope, onError)
    }

    // A native failure is not the end of the road here: the web flow needs only the web client id,
    // which is already configured. Disabled for link-identity, where the two are not equivalent.
    val webFallback = rememberNativeFailureWebFallback(
        provider = KmpSupabaseAuthProvider.GOOGLE,
        client = client,
        enabled = !linkIdentity,
        timeout = timeout,
        scope = watchdogScope,
        onError = onError,
    )
    val watchdog = remember { mutableStateOf<Job?>(null) }
    val onResult: (NativeSignInResult) -> Unit = { result ->
        watchdog.value?.cancel()
        result.reportFailure(KmpSupabaseAuthProvider.GOOGLE, onError, onNativeUnusable = webFallback)
    }
    // `onIdToken` is non-nullable with a plugin-supplied default, so the link-identity variant
    // has to be a separate call rather than a null argument.
    val state = if (linkIdentity) {
        raw.composeAuth.rememberSignInWithGoogle(
            type = dialogType,
            onResult = onResult,
            onIdToken = ComposeAuth.LINK_IDENTITY_CALLBACK,
        )
    } else {
        raw.composeAuth.rememberSignInWithGoogle(type = dialogType, onResult = onResult)
    }
    return rememberSignInWithWatchdog(
        KmpSupabaseAuthProvider.GOOGLE,
        timeout,
        watchdog,
        watchdogScope,
        client,
        onError,
    ) {
        state.startFlow()
    }
}

/**
 * Native Apple sign-in on iOS; GoTrue OAuth redirect elsewhere — including macOS, JVM and web.
 * Same success-reporting caveat and [timeout] behaviour as [rememberKmpSupabaseGoogleSignIn].
 */
@Composable
public fun rememberKmpSupabaseAppleSignIn(
    client: KmpSupabaseAuthClient = koinInject(),
    linkIdentity: Boolean = false,
    timeout: Duration = KmpSupabaseDefaultSignInTimeout,
    watchdogScope: CoroutineScope? = null,
    onError: (KmpSupabaseAuthError) -> Unit = {},
): KmpSupabaseSignInLauncher {
    val raw = client.raw ?: return remember {
        KmpSupabaseAuthLog.log { "APPLE: client not configured — launcher will report NotConfigured" }
        KmpSupabaseSignInLauncher { onError(KmpSupabaseAuthError.NotConfigured) }
    }
    val watchdog = remember { mutableStateOf<Job?>(null) }
    val onResult: (NativeSignInResult) -> Unit = { result ->
        watchdog.value?.cancel()
        result.reportFailure(KmpSupabaseAuthProvider.APPLE, onError)
    }
    val state = if (linkIdentity) {
        raw.composeAuth.rememberSignInWithApple(
            onResult = onResult,
            onIdToken = ComposeAuth.LINK_IDENTITY_CALLBACK,
        )
    } else {
        raw.composeAuth.rememberSignInWithApple(onResult = onResult)
    }
    return rememberSignInWithWatchdog(
        KmpSupabaseAuthProvider.APPLE,
        timeout,
        watchdog,
        watchdogScope,
        client,
        onError,
    ) {
        state.startFlow()
    }
}

/**
 * A launcher that reports [KmpSupabaseAuthError.NoResponse] if the provider never answers.
 *
 * A provider CAN return nothing at all — no success, no error, no cancellation. Verified on an
 * Android device: native Google was launched, Credential Manager showed no UI, and `onResult`
 * never fired, leaving the screen on "Signing in…" indefinitely with nothing in the logs after
 * the handoff.
 *
 * It lives at the LAUNCHER layer, not in [KmpSupabaseAuthViewModel], and that placement is the whole
 * point. A guard in the library's ViewModel protects only apps that adopt it — most bring their
 * own, so the first version of this fix did nothing for the app that exposed the bug. Every
 * sign-in goes through a launcher, so this covers every consumer regardless of their state layer.
 *
 * The watchdog is cancelled by any real result and re-armed on each press.
 */
@Composable
private fun rememberSignInWithWatchdog(
    provider: KmpSupabaseAuthProvider,
    timeout: Duration,
    watchdog: androidx.compose.runtime.MutableState<Job?>,
    watchdogScope: CoroutineScope?,
    client: KmpSupabaseAuthClient,
    onError: (KmpSupabaseAuthError) -> Unit,
    start: () -> Unit,
): KmpSupabaseSignInLauncher {
    // `watchdogScope` matters more than it looks. The default is this composable's scope, which
    // Compose CANCELS when the button leaves composition — and a login screen that swaps its
    // buttons for a spinner while signing in does exactly that. The watchdog then dies at the
    // moment it is needed, which is how the first version of this fix silently did nothing.
    //
    // Pass a scope that outlives the button (one remembered by the SCREEN, or a ViewModel scope)
    // whenever your UI replaces the providers during sign-in.
    val fallbackScope = rememberCoroutineScope()
    val scope = watchdogScope ?: fallbackScope
    return remember(provider, timeout, client, start) {
        KmpSupabaseSignInLauncher {
            KmpSupabaseAuthLog.log { "$provider: startFlow() — handing off to the provider" }
            watchdog.value?.cancel()
            // Snapshot BEFORE handing off, so the foreground arm below can tell "a session landed
            // while we were away" from "an anonymous session was already here".
            val tokenBefore = client.currentAccessToken()
            if (timeout != Duration.INFINITE) {
                watchdog.value = scope.launch {
                    // RACE, deliberately: whichever of these two happens first ends the flow.
                    //
                    //   (a) the app comes back to the foreground with no result  → Cancelled
                    //   (b) nothing at all happens for `timeout`                 → NoResponse
                    //
                    // (a) exists because a DISMISSED provider reports nothing — no success, no
                    // error, no cancellation. MEASURED on mbs/cappy, Android 15, 2026-10-03: the
                    // Custom Tab was destroyed at 12:18:13 and the only thing that ever ended the
                    // flow was this watchdog at 12:20:35. For those two-plus minutes the screen
                    // sat on "Signing in…" with every button disabled, and the error it finally
                    // produced was NoResponse — the wrong class, with a message about signing
                    // certificates — for a person who had simply changed their mind.
                    select<Unit> {
                        async { awaitAppForegroundReturn() }.onAwait {
                            // A real callback lands a beat AFTER the app regains focus (the
                            // provider resumes the host Activity, then delivers its result), so
                            // reporting on a fixed timer turns a SUCCESSFUL sign-in into a
                            // spurious "cancelled" — worse than the bug this guards against.
                            //
                            // A longer timer cannot fix that; only EVIDENCE can. MEASURED on
                            // mbs/cappy, Android 15, 2026-10-21 — a successful native Google
                            // sign-in, where the old 1.5s grace expired 276ms before the result:
                            //
                            //   18:46:47.550  …no result after 1.5s — treating as cancelled
                            //   18:46:47.826  onResult = Success
                            //
                            // So wait for the ACCESS TOKEN to change instead. This ends the moment
                            // a session lands, and only runs out when the person really did back
                            // out — which is why it can afford to be longer than any safe timer.
                            val signedIn = withTimeoutOrNull(SessionSettleWindow) {
                                while (client.currentAccessToken() == tokenBefore) delay(SessionSettlePoll)
                            } != null
                            if (signedIn) {
                                KmpSupabaseAuthLog.log {
                                    "$provider: a session landed after the app returned — the flow " +
                                        "succeeded even if the provider never called back"
                                }
                            } else {
                                KmpSupabaseAuthLog.log {
                                    "$provider: app returned to the foreground and no session " +
                                        "appeared within $SessionSettleWindow — treating as " +
                                        "cancelled by the user"
                                }
                                onError(KmpSupabaseAuthError.Cancelled)
                            }
                        }
                        async { delay(timeout) }.onAwait {
                            KmpSupabaseAuthLog.log {
                                "$provider: TIMEOUT after $timeout — the provider never called " +
                                    "back and the app never returned to the foreground (no " +
                                    "success, no error, no cancellation). Causes seen in the " +
                                    "wild: this build's signing certificate is not registered " +
                                    "against the OAuth client, or the provider UI never appeared."
                            }
                            onError(KmpSupabaseAuthError.NoResponse)
                        }
                    }
                }
            }
            start()
        }
    }
}

/**
 * Maps the plugin's result onto the provider-neutral taxonomy. Success is intentionally ignored.
 *
 * @param onNativeUnusable if non-null, a [NativeSignInResult.Error] hands off to this instead of
 *   reporting [KmpSupabaseAuthError.ProviderRejected]. See [rememberNativeFailureWebFallback].
 */
internal fun NativeSignInResult.reportFailure(
    provider: KmpSupabaseAuthProvider,
    onError: (KmpSupabaseAuthError) -> Unit,
    onNativeUnusable: (() -> Unit)? = null,
) {
    // Logged for EVERY branch, success included. A provider that returns nothing at all is
    // indistinguishable from one that was never launched, and that ambiguity is exactly what makes
    // a stuck "Signing in…" impossible to diagnose from the outside: silence here means the
    // callback never fired, which is a different bug from a reported failure.
    when (this) {
        is NativeSignInResult.Success -> KmpSupabaseAuthLog.log { "$provider: onResult = Success" }

        // sessionStatus is the source of truth
        is NativeSignInResult.ClosedByUser -> {
            KmpSupabaseAuthLog.log { "$provider: onResult = ClosedByUser (dismissed)" }
            onError(KmpSupabaseAuthError.Cancelled)
        }

        is NativeSignInResult.NetworkError -> {
            KmpSupabaseAuthLog.log { "$provider: onResult = NetworkError: $message" }
            onError(KmpSupabaseAuthError.Network())
        }

        // The provider's own message and exception are the ONLY description of WHY this failed,
        // and they were being discarded — `ProviderRejected(provider)` says which provider and
        // nothing else, which is useless on a device where the sheet appears and then fails.
        // Typical content here: an unregistered signing certificate, or a client id that does not
        // match the calling package.
        is NativeSignInResult.Error -> {
            KmpSupabaseAuthLog.logError(exception) { "$provider: onResult = Error: $message" }
            if (onNativeUnusable != null) {
                KmpSupabaseAuthLog.log {
                    "$provider: the native path cannot sign in on this device — falling back to the " +
                        "web OAuth flow, which needs only the web client id"
                }
                onNativeUnusable()
            } else {
                onError(KmpSupabaseAuthError.ProviderRejected(provider, exception))
            }
        }
    }
}

/**
 * How long to wait, after the app comes back from the web leg, for the session to actually land.
 *
 * The Android web leg is FIRE-AND-FORGET: `launchWebOAuth` is `auth.signInWith(Google, …)`, which
 * returns the moment the Custom Tab is launched — the session arrives later, over the deep-link
 * callback. So "the app is in the foreground again" says nothing on its own about whether the
 * person signed in or dismissed the tab, and the only honest way to tell them apart is to watch
 * for the access token to change.
 *
 * This is a CEILING, not a delay: the wait ends as soon as the token changes. It only runs out when
 * the tab really was dismissed.
 */
private val SessionSettleWindow: Duration = 3.seconds

/** How often the settle window re-checks the access token. */
private val SessionSettlePoll: Duration = 150.milliseconds

/**
 * The web-OAuth leg used as a RECOVERY when the native provider cannot sign in on this device.
 *
 * Android's native Google path (`GetGoogleIdOption`) validates the calling app — package name plus
 * signing certificate — against an **Android OAuth client** in the same Google Cloud project as the
 * web client id. Several ordinary situations leave it with nothing to offer, and every one of them
 * surfaces identically as `NoCredentialException: "No credentials available"`:
 *
 * - no Android OAuth client registered for this package + signing SHA-1 (**every fresh fork**, and
 *   MEASURED on mbs/cappy 2026-10-12: `[GetGoogleIdOperation] Operation failed. curh: [28444]` with
 *   seven Google accounts signed in on the device)
 * - a build signed by a certificate that was never registered (debug keystore, Play App Signing)
 * - no Google account on the device at all
 * - Credential Manager's cooldown after repeated dismissals
 *
 * None of those are failures of the APP, and in all of them the web flow works — it needs only the
 * web client id, which is already configured for the native path to have been attempted. Reporting
 * [KmpSupabaseAuthError.ProviderRejected] here instead would dead-end a sign-in that could have
 * succeeded, which is what it did before this existed.
 *
 * Returns null when there is nothing safe to fall back TO — see the [enabled] parameter.
 *
 * @param enabled false disables the fallback and restores the plain `ProviderRejected` report.
 *   Passed false for link-identity, where the two paths are NOT equivalent: linking attaches the
 *   provider to the CURRENT session, while the web leg signs in as a NEW one. Silently swapping
 *   those would replace a guest session — and lose whatever it holds — to avoid an error message.
 */
@Composable
private fun rememberNativeFailureWebFallback(
    provider: KmpSupabaseAuthProvider,
    client: KmpSupabaseAuthClient,
    enabled: Boolean,
    timeout: Duration,
    scope: CoroutineScope?,
    onError: (KmpSupabaseAuthError) -> Unit,
): (() -> Unit)? {
    if (!enabled) return null
    // Detached for the same measured reason as the other web leg: the recovery outlives the button,
    // and rememberCoroutineScope() dies exactly when a login screen swaps its providers for a
    // spinner.
    val detachedScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val active = scope ?: detachedScope
    return remember(provider, client, active, timeout, onError) {
        {
            active.launch {
                // Snapshot BEFORE launching. A plain "is there a token now?" check would read an
                // existing anonymous/guest session as proof that this sign-in worked.
                val tokenBefore = client.currentAccessToken()
                val launched = when (provider) {
                    KmpSupabaseAuthProvider.APPLE -> client.signInWithAppleFallback()
                    else -> client.signInWithGoogleFallback()
                }
                launched.onFailure { cause ->
                    val error = cause as? KmpSupabaseAuthError ?: KmpSupabaseAuthError.Unknown(cause)
                    KmpSupabaseAuthLog.logError(error.cause) {
                        "$provider: web OAuth fallback could not start (${error::class.simpleName}). " +
                            "The native path had already failed, so there is nothing left to try."
                    }
                    onError(error)
                    return@launch
                }
                // The tab is open. Exactly one of these ends the flow.
                select<Unit> {
                    async { awaitAppForegroundReturn() }.onAwait {
                        val signedIn = withTimeoutOrNull(SessionSettleWindow) {
                            while (client.currentAccessToken() == tokenBefore) delay(SessionSettlePoll)
                        } != null
                        if (signedIn) {
                            KmpSupabaseAuthLog.log { "$provider: web OAuth fallback signed in" }
                        } else {
                            KmpSupabaseAuthLog.log {
                                "$provider: app returned from the web OAuth fallback with no session " +
                                    "after $SessionSettleWindow — treating as cancelled by the user"
                            }
                            onError(KmpSupabaseAuthError.Cancelled)
                        }
                    }
                    async { delay(timeout) }.onAwait {
                        KmpSupabaseAuthLog.log {
                            "$provider: web OAuth fallback TIMEOUT after $timeout — the app never came " +
                                "back from the browser. Check that this app's redirect URL is listed " +
                                "in Supabase; a missing one completes the sheet and drops the callback."
                        }
                        onError(KmpSupabaseAuthError.NoResponse)
                    }
                }
            }
        }
    }
}

/**
 * Drives [KmpSupabaseAuthClient.signInWithGoogleFallback] / `signInWithAppleFallback` — the
 * library's own in-app web OAuth leg (`ASWebAuthenticationSession` on iOS).
 *
 * Uses a DETACHED scope for the same measured reason as guest sign-in: presenting the sheet and
 * then navigating on success tears down this composition, and `rememberCoroutineScope()` is
 * cancelled at exactly that moment — which reads as "sign-in silently does nothing".
 *
 * No watchdog. The suspend call returns a `Result` either way, so the no-callback failure mode the
 * watchdog exists for cannot happen here.
 */
@Composable
private fun rememberWebOAuthSignIn(
    provider: KmpSupabaseAuthProvider,
    client: KmpSupabaseAuthClient,
    scope: CoroutineScope?,
    onError: (KmpSupabaseAuthError) -> Unit,
): KmpSupabaseSignInLauncher {
    val detachedScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val active = scope ?: detachedScope
    return remember(provider, client, active, onError) {
        KmpSupabaseSignInLauncher {
            KmpSupabaseAuthLog.log { "$provider: in-app web OAuth (no native path on this platform)" }
            active.launch {
                val result = when (provider) {
                    KmpSupabaseAuthProvider.APPLE -> client.signInWithAppleFallback()
                    else -> client.signInWithGoogleFallback()
                }
                result
                    .onSuccess { KmpSupabaseAuthLog.log { "$provider: web OAuth leg completed" } }
                    .onFailure { cause ->
                        val error = cause as? KmpSupabaseAuthError ?: KmpSupabaseAuthError.Unknown(cause)
                        KmpSupabaseAuthLog.logError(error.cause) {
                            "$provider: web OAuth FAILED (${error::class.simpleName}). Check that the " +
                                "provider is enabled in Supabase and that the app's redirect URL is " +
                                "listed there — a missing redirect URL completes the sheet and then " +
                                "drops the callback."
                        }
                        onError(error)
                    }
            }
        }
    }
}
