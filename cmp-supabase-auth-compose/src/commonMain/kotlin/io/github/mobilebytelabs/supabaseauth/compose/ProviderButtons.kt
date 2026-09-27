package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.compose.auth.ui.ProviderButtonContent
import io.github.jan.supabase.compose.auth.ui.annotations.AuthUiExperimental

/**
 * Provider buttons.
 *
 * The marks come from ComposeAuthUI's `ProviderButtonContent`, not hand-drawn icons: Apple and
 * Google both publish brand requirements — Apple mandates the exact wording — and a bespoke mark
 * risks store review.
 *
 * ComposeAuthUI is flagged EXPERIMENTAL upstream, so its opt-in is required. Containing the
 * `@OptIn` here — rather than switching it on repo-wide with a compiler flag — is deliberate:
 * this file becomes the single blast radius for an upstream API break, and consuming apps never
 * have to opt in to an experimental API they did not choose.
 */
@OptIn(AuthUiExperimental::class)
@Composable
public fun GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        ProviderButtonContent(Google)
    }
}

@OptIn(AuthUiExperimental::class)
@Composable
public fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        ProviderButtonContent(Apple)
    }
}

@Composable
public fun ContinueAsGuestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Continue as guest",
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Text(text)
    }
}
