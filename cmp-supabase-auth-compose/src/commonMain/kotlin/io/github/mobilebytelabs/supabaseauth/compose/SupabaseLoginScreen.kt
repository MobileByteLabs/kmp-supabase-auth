package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthClient

/**
 * Drop-in login screen.
 *
 * Slot-based rather than configurable-by-flags: [header] and [footer] let an app brand it without
 * forking, and anything more bespoke should compose [GoogleSignInButton] / [AppleSignInButton] /
 * [ContinueAsGuestButton] against [SupabaseAuthViewModel] directly.
 *
 * [onSignedIn] fires from the SESSION, not from a provider callback — see
 * [SupabaseAuthViewModel]. That is what makes the screen correct on Android, where the native
 * Google success callback often never arrives.
 */
@Composable
public fun SupabaseLoginScreen(
    viewModel: SupabaseAuthViewModel,
    client: SupabaseAuthClient,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    showGoogle: Boolean = true,
    showApple: Boolean = true,
    showGuestOption: Boolean = true,
    onSignedIn: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    val google = rememberGoogleSignIn(client, onError = viewModel::onSignInFailed)
    val apple = rememberAppleSignIn(client, onError = viewModel::onSignInFailed)

    LaunchedEffect(state.isSignedIn) {
        if (state.isSignedIn) onSignedIn()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            header()
            Spacer(Modifier.height(24.dp))

            if (state.isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
            }

            if (showGoogle) {
                GoogleSignInButton(
                    onClick = {
                        viewModel.onSignInStarted()
                        google.launch()
                    },
                    enabled = !state.isLoading,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (showApple) {
                AppleSignInButton(
                    onClick = {
                        viewModel.onSignInStarted()
                        apple.launch()
                    },
                    enabled = !state.isLoading,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (showGuestOption) {
                // Equal-weight, never buried: an app that offers a guest path should not make it
                // feel like a failure state.
                ContinueAsGuestButton(
                    onClick = viewModel::continueAsGuest,
                    enabled = !state.isLoading,
                )
            }

            state.error?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error.message ?: "Sign-in failed",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
            }

            Spacer(Modifier.height(24.dp))
            footer()
        }
    }
}
