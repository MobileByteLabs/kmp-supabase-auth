package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mobilebytelabs.supabaseauth.AuthError
import io.github.mobilebytelabs.supabaseauth.AuthProvider
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

/**
 * One launcher for any provider — `rememberSignIn(client, GOOGLE)` instead of a per-provider call.
 *
 * The per-provider functions remain public for callers that want one explicitly, but a screen
 * offering several providers had to branch on the provider anyway, and that branch is the same
 * in every app. Taking [AuthProvider] moves it here.
 *
 * Providers with no interactive flow (`ANONYMOUS`, `EMAIL`, `OTHER`) return a launcher that
 * reports [AuthError.ProviderRejected] rather than doing nothing — a button that silently does
 * nothing is the hardest kind of broken to notice. For a guest session use
 * `AuthRepository.continueAsGuest()`, which is not a UI flow.
 */
@Composable
public fun rememberSignIn(
    client: SupabaseAuthClient,
    provider: AuthProvider,
    linkIdentity: Boolean = false,
    timeout: Duration = DefaultSignInTimeout,
    watchdogScope: CoroutineScope? = null,
    onError: (AuthError) -> Unit = {},
): SignInLauncher = when (provider) {
    // Named arguments deliberately: these were positional, and inserting `dialogType` silently
    // bound `onError` to it — a type error here, but the same shape in a same-typed pair would
    // compile and misbehave.
    AuthProvider.GOOGLE -> rememberGoogleSignIn(
        client = client,
        linkIdentity = linkIdentity,
        timeout = timeout,
        watchdogScope = watchdogScope,
        onError = onError,
    )

    AuthProvider.APPLE -> rememberAppleSignIn(
        client = client,
        linkIdentity = linkIdentity,
        timeout = timeout,
        watchdogScope = watchdogScope,
        onError = onError,
    )
    else -> remember(provider) { SignInLauncher { onError(AuthError.ProviderRejected(provider)) } }
}

/**
 * A provider sign-in button that is already wired — press it and the flow runs.
 *
 * ```kotlin
 * SupabaseSignInButton(client, AuthProvider.GOOGLE, onError = viewModel::onSignInFailed)
 * SupabaseSignInButton(client, AuthProvider.APPLE,  onError = viewModel::onSignInFailed)
 * ```
 *
 * The button and the launcher ship together on purpose. [GoogleSignInButton] / [AppleSignInButton]
 * take a bare `onClick`, which leaves every app to remember a launcher, hold it in the right
 * scope, and call `launch()` — the step where consumers reached past this library into supabase-kt's
 * `rememberSignInWithGoogle` and re-implemented the native/fallback branch by hand. There is
 * nothing app-specific in that wiring, so it belongs here.
 *
 * **Disabled when the client is not configured.** A blank project ref or anon key means sign-in
 * cannot work; a pressable button that fails is worse than a visibly unavailable one. Pass
 * `enabled = false` to disable it for your own reasons on top of that.
 *
 * Success is deliberately not a parameter. On Android the native Google `onResult(Success)`
 * callback frequently never fires even though the exchange succeeded, so a screen that waits for
 * it hangs while the person is already signed in. Observe `AuthRepository.session` and act on
 * `isAuthenticated` — [onError] is still worth passing, because Cancelled / Network /
 * ProviderRejected do fire reliably.
 */
@Composable
public fun SupabaseSignInButton(
    client: SupabaseAuthClient,
    provider: AuthProvider,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    linkIdentity: Boolean = false,
    timeout: Duration = DefaultSignInTimeout,
    /**
     * Scope for the no-response watchdog. Pass one owned by the SCREEN when your UI replaces these
     * buttons with a spinner during sign-in — otherwise the watchdog is cancelled with the button.
     */
    watchdogScope: CoroutineScope? = null,
    onClick: () -> Unit = {},
    onError: (AuthError) -> Unit = {},
) {
    val launcher = rememberSignIn(
        client = client,
        provider = provider,
        linkIdentity = linkIdentity,
        timeout = timeout,
        watchdogScope = watchdogScope,
        onError = onError,
    )
    val usable = enabled && client.isConfigured

    // The app's hook runs FIRST, then the flow launches. Apps dispatch an analytics event or a
    // typed UI action here — the press is the app's business, the flow is the library's. Firing
    // it after `launch()` would drop the event whenever a provider sheet opens synchronously.
    val press = {
        onClick()
        launcher.launch()
    }

    when (provider) {
        AuthProvider.GOOGLE -> GoogleSignInButton(
            onClick = press,
            modifier = modifier,
            enabled = usable,
        )

        AuthProvider.APPLE -> AppleSignInButton(
            onClick = press,
            modifier = modifier,
            enabled = usable,
        )

        // No interactive flow to render. Silently drawing nothing would leave a gap in the layout
        // that reads as a styling bug; the caller asked for a provider this button cannot show.
        AuthProvider.ANONYMOUS, AuthProvider.EMAIL, AuthProvider.OTHER -> Unit
    }
}
