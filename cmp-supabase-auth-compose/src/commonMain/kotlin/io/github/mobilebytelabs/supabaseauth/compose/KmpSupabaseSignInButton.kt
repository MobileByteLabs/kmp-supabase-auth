package io.github.mobilebytelabs.supabaseauth.compose

import org.koin.compose.koinInject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthClient
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

/**
 * One launcher for any provider — `rememberKmpSupabaseSignIn(client, GOOGLE)` instead of a per-provider call.
 *
 * The per-provider functions remain public for callers that want one explicitly, but a screen
 * offering several providers had to branch on the provider anyway, and that branch is the same
 * in every app. Taking [KmpSupabaseAuthProvider] moves it here.
 *
 * Providers with no interactive flow (`ANONYMOUS`, `EMAIL`, `OTHER`) return a launcher that
 * reports [KmpSupabaseAuthError.ProviderRejected] rather than doing nothing — a button that silently does
 * nothing is the hardest kind of broken to notice. For a guest session use
 * `KmpSupabaseAuthRepository.continueAsGuest()`, which is not a UI flow.
 */
@Composable
public fun rememberKmpSupabaseSignIn(
    provider: KmpSupabaseAuthProvider,
    client: KmpSupabaseAuthClient = koinInject(),
    linkIdentity: Boolean = false,
    timeout: Duration = KmpSupabaseDefaultSignInTimeout,
    watchdogScope: CoroutineScope? = null,
    onError: (KmpSupabaseAuthError) -> Unit = {},
): KmpSupabaseSignInLauncher = when (provider) {
    // Named arguments deliberately: these were positional, and inserting `dialogType` silently
    // bound `onError` to it — a type error here, but the same shape in a same-typed pair would
    // compile and misbehave.
    KmpSupabaseAuthProvider.GOOGLE -> rememberKmpSupabaseGoogleSignIn(
        client = client,
        linkIdentity = linkIdentity,
        timeout = timeout,
        watchdogScope = watchdogScope,
        onError = onError,
    )

    KmpSupabaseAuthProvider.APPLE -> rememberKmpSupabaseAppleSignIn(
        client = client,
        linkIdentity = linkIdentity,
        timeout = timeout,
        watchdogScope = watchdogScope,
        onError = onError,
    )
    // ANONYMOUS is a REAL flow now — it was previously rejected here, which is what forced every
    // consumer to reach past this function to the repository for a guest session.
    KmpSupabaseAuthProvider.ANONYMOUS -> rememberKmpSupabaseGuestSignIn(scope = watchdogScope, onError = onError)

    else -> remember(provider) { KmpSupabaseSignInLauncher { onError(KmpSupabaseAuthError.ProviderRejected(provider)) } }
}

/**
 * A provider sign-in button that is already wired — press it and the flow runs.
 *
 * ```kotlin
 * KmpSupabaseSignInButton(client, KmpSupabaseAuthProvider.GOOGLE, onError = viewModel::onSignInFailed)
 * KmpSupabaseSignInButton(client, KmpSupabaseAuthProvider.APPLE,  onError = viewModel::onSignInFailed)
 * ```
 *
 * The button and the launcher ship together on purpose. [KmpSupabaseGoogleSignInButton] / [KmpSupabaseAppleSignInButton]
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
 * it hangs while the person is already signed in. Observe `KmpSupabaseAuthRepository.session` and act on
 * `isAuthenticated` — [onError] is still worth passing, because Cancelled / Network /
 * ProviderRejected do fire reliably.
 */
@Composable
public fun KmpSupabaseSignInButton(
    provider: KmpSupabaseAuthProvider,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    linkIdentity: Boolean = false,
    timeout: Duration = KmpSupabaseDefaultSignInTimeout,
    client: KmpSupabaseAuthClient = koinInject(),
    /**
     * Scope for the no-response watchdog. Pass one owned by the SCREEN when your UI replaces these
     * buttons with a spinner during sign-in — otherwise the watchdog is cancelled with the button.
     */
    scope: CoroutineScope? = null,
    onClick: () -> Unit = {},
    onError: (KmpSupabaseAuthError) -> Unit = {},
) {
    // Dispatches to the per-provider buttons rather than re-implementing them: each one owns its
    // own brand presentation (Apple's permitted variants are not Google's), and ANONYMOUS is a
    // first-class third option here, not a gap the caller has to fill.
    when (provider) {
        KmpSupabaseAuthProvider.GOOGLE -> KmpSupabaseGoogleSignInButton(
            modifier = modifier, enabled = enabled, height = KmpSupabaseSignInButtonHeight,
            client = client, linkIdentity = linkIdentity, timeout = timeout, scope = scope,
            onClick = onClick, onError = onError,
        )

        KmpSupabaseAuthProvider.APPLE -> KmpSupabaseAppleSignInButton(
            modifier = modifier, enabled = enabled, height = KmpSupabaseSignInButtonHeight,
            client = client, linkIdentity = linkIdentity, timeout = timeout, scope = scope,
            onClick = onClick, onError = onError,
        )

        KmpSupabaseAuthProvider.ANONYMOUS -> KmpSupabaseContinueAsGuestButton(
            modifier = modifier, enabled = enabled, scope = scope,
            onClick = onClick, onError = onError,
        )

        // No interactive flow to render. Drawing nothing silently would leave a gap in the layout
        // that reads as a styling bug; the caller asked for a provider this button cannot show.
        KmpSupabaseAuthProvider.EMAIL, KmpSupabaseAuthProvider.OTHER -> Unit
    }
}
