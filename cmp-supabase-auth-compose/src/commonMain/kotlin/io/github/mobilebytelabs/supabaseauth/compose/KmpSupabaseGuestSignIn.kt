package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthLog
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * A launcher for the anonymous ("guest") session, shaped exactly like the provider launchers.
 *
 * Guest was the one path a consumer still had to wire itself — it meant knowing that "continue as
 * guest" is `repository.continueAsGuest()`, that it suspends, that it needs a scope, and that its
 * failure is a [KmpSupabaseAuthError]. None of that is app-specific, and leaving it out made guest
 * the odd one out on a screen where Google and Apple were already one call each.
 *
 * Success is deliberately NOT reported, for the same reason as the provider launchers: the session
 * flow is the source of truth. Observe [rememberKmpSupabaseAuthState] and react to `isGuest`.
 */
@Composable
public fun rememberKmpSupabaseGuestSignIn(
    repository: KmpSupabaseAuthRepository = koinInject(),
    scope: CoroutineScope? = null,
    onError: (KmpSupabaseAuthError) -> Unit = {},
): KmpSupabaseSignInLauncher {
    // NOT rememberCoroutineScope(). Creating an anonymous session is fire-and-forget by design:
    // the press usually ALSO navigates the person into the app, which tears down this composition
    // — and `rememberCoroutineScope()` is cancelled at exactly that moment, killing the in-flight
    // request. Verified on device 2026-10-01: the call died with
    // `ForgottenCoroutineScopeException: rememberCoroutineScope left the composition`, which read
    // as "anonymous sign-in is broken" until the log said otherwise.
    //
    // A scope built here is remembered but never cancelled by Compose, so the request completes
    // even as the screen goes away. That is correct for this action and wrong for the provider
    // watchdogs, which SHOULD die with their attempt — hence the difference.
    val detachedScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val active = scope ?: detachedScope
    return remember(repository, active, onError) {
        KmpSupabaseSignInLauncher {
            KmpSupabaseAuthLog.log { "ANONYMOUS: continueAsGuest() — requesting an anonymous session" }
            active.launch {
                repository.continueAsGuest()
                    .onSuccess { KmpSupabaseAuthLog.log { "ANONYMOUS: session created" } }
                    .onFailure { cause ->
                        val error = cause as? KmpSupabaseAuthError ?: KmpSupabaseAuthError.Unknown(cause)
                        // Logged BEFORE onError, and unconditionally. The default onError is a
                        // no-op, so without this a rejected request produced absolutely nothing —
                        // no log, no UI, no clue. Verified on device 2026-10-01: the backend had
                        // anonymous sign-ins DISABLED and the only way to discover it was to query
                        // the project's auth config by hand. That is the exact "silently does
                        // nothing" failure this library exists to make impossible.
                        KmpSupabaseAuthLog.logError(error.cause) {
                            "ANONYMOUS: continueAsGuest() FAILED (${error::class.simpleName}). Most often the " +
                                "backend has anonymous sign-ins disabled — Supabase: Authentication → " +
                                "Providers → Anonymous. Nothing is wrong with the client when this is the cause."
                        }
                        onError(error)
                    }
            }
        }
    }
}

/**
 * "Continue as guest" — the third first-class button, already wired.
 *
 * Peer of [KmpSupabaseGoogleSignInButton] / [KmpSupabaseAppleSignInButton], and like
 * `KmpSupabaseSignInButton(client, provider)` it needs no repository, no scope, and no knowledge
 * that "guest" means an anonymous GoTrue session. Press it and the session is created.
 *
 * **[onClick] runs FIRST, then the session is requested — and that order is load-bearing for a
 * local-first app.** The anonymous session is a network round-trip; a guest must land in the app
 * even with no network. Put the local "proceed" in [onClick] and it is never gated on the request.
 * When the network IS there the anonymous session also lands, which is what later allows the guest
 * to be UPGRADED to a real account (`linkIdentity = true`) rather than starting over.
 *
 * @param prominent render as a filled button rather than a text button. Default false: guest is an
 *   escape, not a third provider, and giving it equal weight pushes people away from the accounts
 *   that let their data survive a reinstall. Set true when guest is genuinely the primary path —
 *   e.g. an app whose cloud sign-in is optional, or one running unconfigured.
 */
@Composable
public fun KmpSupabaseContinueAsGuestButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Continue as guest",
    height: Dp = KmpSupabaseSignInButtonHeight,
    prominent: Boolean = false,
    colors: ButtonColors? = null,
    repository: KmpSupabaseAuthRepository = koinInject(),
    scope: CoroutineScope? = null,
    onClick: () -> Unit = {},
    onError: (KmpSupabaseAuthError) -> Unit = {},
) {
    val launcher = rememberKmpSupabaseGuestSignIn(repository = repository, scope = scope, onError = onError)
    val press = {
        onClick()
        launcher.launch()
    }
    // Shape and height match the provider buttons so the three read as one set. Everything that
    // carries BRAND — the container and content colours — comes from the consumer's MaterialTheme
    // rather than a constant baked in here, because guest is the one button with no third-party
    // brand to honour: Google and Apple dictate their presentation, this one is the app's.
    val shape = RoundedCornerShape(12.dp)
    val sized = modifier.fillMaxWidth().defaultMinSize(minHeight = height).height(height)

    if (prominent) {
        Button(
            onClick = press,
            enabled = enabled,
            shape = shape,
            // Theme-derived, overridable. `colors` stays available for an app that wants something
            // its scheme does not express.
            colors = colors ?: ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = sized,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    } else {
        // DEFAULT: an outlined button matching the providers' silhouette, themed by the consumer.
        // A bare TextButton made guest look like a disabled link beside two solid-bordered
        // providers — visually a different class of control rather than a quieter option.
        OutlinedButton(
            onClick = press,
            enabled = enabled,
            shape = shape,
            colors = colors ?: ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = sized,
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Sign out, already wired.
 *
 * The other non-provider action, here for the same reason as the guest launcher: a consumer should
 * not have to inject the repository, remember a scope, and know that sign-out suspends, just to put
 * a "Sign out" row in settings. Local state is cleared even if the remote call fails — see
 * `KmpSupabaseAuthSessionStore.clear`.
 */
@Composable
public fun rememberKmpSupabaseSignOut(
    repository: KmpSupabaseAuthRepository = koinInject(),
    scope: CoroutineScope? = null,
): () -> Unit {
    // Detached for the same reason as the guest launcher: signing out flips the auth state, which
    // routes the person away and tears down whatever composition the button lived in — cancelling
    // a `rememberCoroutineScope()` mid-request and leaving the remote session alive on the server.
    val detachedScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val active = scope ?: detachedScope
    return remember(repository, active) { { active.launch { repository.signOut() } } }
}
