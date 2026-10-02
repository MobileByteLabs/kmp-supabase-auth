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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration
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

    val watchdog = remember { mutableStateOf<Job?>(null) }
    val onResult: (NativeSignInResult) -> Unit = { result ->
        watchdog.value?.cancel()
        result.reportFailure(KmpSupabaseAuthProvider.GOOGLE, onError)
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
    return rememberSignInWithWatchdog(KmpSupabaseAuthProvider.GOOGLE, timeout, watchdog, watchdogScope, onError) {
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
    return rememberSignInWithWatchdog(KmpSupabaseAuthProvider.APPLE, timeout, watchdog, watchdogScope, onError) {
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
    return remember(provider, timeout, start) {
        KmpSupabaseSignInLauncher {
            KmpSupabaseAuthLog.log { "$provider: startFlow() — handing off to the provider" }
            watchdog.value?.cancel()
            if (timeout != Duration.INFINITE) {
                watchdog.value = scope.launch {
                    delay(timeout)
                    KmpSupabaseAuthLog.log {
                        "$provider: TIMEOUT after $timeout — the provider never called back " +
                            "(no success, no error, no cancellation). Most often this build's " +
                            "signing certificate is not registered against the OAuth client."
                    }
                    onError(KmpSupabaseAuthError.NoResponse)
                }
            }
            start()
        }
    }
}

/** Maps the plugin's result onto the provider-neutral taxonomy. Success is intentionally ignored. */
private fun NativeSignInResult.reportFailure(
    provider: KmpSupabaseAuthProvider,
    onError: (KmpSupabaseAuthError) -> Unit,
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
            onError(KmpSupabaseAuthError.ProviderRejected(provider, exception))
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
