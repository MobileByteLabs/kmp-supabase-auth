package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.composable.NativeSignInResult
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithApple
import io.github.jan.supabase.compose.auth.composable.rememberSignInWithGoogle
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.mobilebytelabs.supabaseauth.AuthError
import io.github.mobilebytelabs.supabaseauth.AuthProvider
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthClient

/**
 * Native Google sign-in — Credential Manager on Android, native on iOS, GoTrue OAuth redirect
 * everywhere else (the plugin chooses).
 *
 * [onError] reports only genuine failures. **Success is deliberately NOT reported here.** On
 * Android the `NativeSignInResult.Success` callback frequently never fires even though the
 * id_token exchange succeeded and the session landed, so a UI waiting on it hangs on a spinner
 * while the person is already signed in. Observe `AuthRepository.isSignedIn` instead —
 * [SupabaseAuthViewModel] already does.
 *
 * Pass [linkIdentity] to attach this provider to the CURRENT session rather than starting a new
 * one — that is the guest-to-account upgrade, and the plugin implements it via
 * `ComposeAuth.LINK_IDENTITY_CALLBACK`.
 */
@Composable
public fun rememberGoogleSignIn(
    client: SupabaseAuthClient,
    linkIdentity: Boolean = false,
    onError: (AuthError) -> Unit = {},
): SignInLauncher {
    val raw = client.raw ?: return remember { SignInLauncher { onError(AuthError.NotConfigured) } }
    // `onIdToken` is non-nullable with a plugin-supplied default, so the link-identity variant
    // has to be a separate call rather than a null argument.
    val state = if (linkIdentity) {
        raw.composeAuth.rememberSignInWithGoogle(
            onResult = { it.reportFailure(AuthProvider.GOOGLE, onError) },
            onIdToken = ComposeAuth.LINK_IDENTITY_CALLBACK,
        )
    } else {
        raw.composeAuth.rememberSignInWithGoogle(
            onResult = { it.reportFailure(AuthProvider.GOOGLE, onError) },
        )
    }
    return remember(state) { SignInLauncher { state.startFlow() } }
}

/**
 * Native Apple sign-in on iOS; GoTrue OAuth redirect elsewhere — including macOS, JVM and web.
 * Same success-reporting caveat as [rememberGoogleSignIn].
 */
@Composable
public fun rememberAppleSignIn(
    client: SupabaseAuthClient,
    linkIdentity: Boolean = false,
    onError: (AuthError) -> Unit = {},
): SignInLauncher {
    val raw = client.raw ?: return remember { SignInLauncher { onError(AuthError.NotConfigured) } }
    val state = if (linkIdentity) {
        raw.composeAuth.rememberSignInWithApple(
            onResult = { it.reportFailure(AuthProvider.APPLE, onError) },
            onIdToken = ComposeAuth.LINK_IDENTITY_CALLBACK,
        )
    } else {
        raw.composeAuth.rememberSignInWithApple(
            onResult = { it.reportFailure(AuthProvider.APPLE, onError) },
        )
    }
    return remember(state) { SignInLauncher { state.startFlow() } }
}

/** Maps the plugin's result onto the provider-neutral taxonomy. Success is intentionally ignored. */
private fun NativeSignInResult.reportFailure(provider: AuthProvider, onError: (AuthError) -> Unit) {
    when (this) {
        is NativeSignInResult.Success -> Unit

        // sessionStatus is the source of truth
        is NativeSignInResult.ClosedByUser -> onError(AuthError.Cancelled)

        is NativeSignInResult.NetworkError -> onError(AuthError.Network())

        is NativeSignInResult.Error -> onError(AuthError.ProviderRejected(provider))
    }
}
