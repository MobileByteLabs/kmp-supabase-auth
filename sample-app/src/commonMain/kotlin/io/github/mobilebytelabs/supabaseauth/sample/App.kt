package io.github.mobilebytelabs.supabaseauth.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuth
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthConfig

/**
 * Sample app for KMP Supabase Auth.
 *
 * Today it exercises the part of the library that actually ships: configuration and its
 * validation rules, which are where most setup mistakes surface. Type a placeholder ref or blank
 * out the client id and watch what the library decides — that is the real contract, not a
 * decorative demo.
 *
 * The sign-in screen lands here once `cmp-supabase-auth-compose` is implemented, and it matters
 * that it lands HERE: native Credential Manager and ASAuthorization cannot be exercised by any CI
 * job, so a runnable sample on a physical device is the only honest verification of those paths.
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            var projectRef by remember { mutableStateOf("abcdefgh") }
            var googleWebClientId by remember { mutableStateOf("123.apps.googleusercontent.com") }
            var redirectUrl by remember { mutableStateOf("myapp://login-callback") }

            val config = KmpSupabaseAuthConfig(
                projectRef = projectRef,
                googleWebClientId = googleWebClientId,
                redirectUrl = redirectUrl,
            )

            // `validate` throws only on a blank/placeholder projectRef — the one condition no
            // fallback can rescue. Everything else degrades rather than failing.
            val validation = runCatching { KmpSupabaseAuth.validate(config) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("KMP Supabase Auth", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = projectRef,
                    onValueChange = { projectRef = it },
                    label = { Text("projectRef") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = googleWebClientId,
                    onValueChange = { googleWebClientId = it },
                    label = { Text("googleWebClientId (Web id, not Android)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = redirectUrl,
                    onValueChange = { redirectUrl = it },
                    label = { Text("redirectUrl") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row("Configured", config.isConfigured.toString())
                        Row("Native Google", config.hasGoogleNative.toString())
                        Row("OAuth scheme", config.oauthScheme ?: "—")
                        Row("OAuth host", config.oauthHost ?: "—")
                        Row(
                            "validate()",
                            validation.fold(
                                onSuccess = { "accepted" },
                                onFailure = { "rejected: ${it.message?.take(60)}…" },
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(onClick = { projectRef = "YOUR_PROJECT_REF" }) {
                    Text("Try a placeholder projectRef")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { googleWebClientId = "" }) {
                    Text("Try a blank client id")
                }
            }
        }
    }
}

@Composable
private fun Row(label: String, value: String) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
}
