package io.github.mobilebytelabs.supabaseauth.compose

import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.composeAuth
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthClient

/**
 * The underlying [ComposeAuth] plugin, or null when the client is not configured.
 *
 * Lives HERE rather than on `KmpSupabaseAuthClient` in the headless module, and that placement is the
 * whole point: `ComposeAuth` comes from `compose-auth`, which publishes 7 targets. Declaring it on
 * the headless interface would drag that dependency into a module that currently reaches 17, so
 * every consumer with no Compose at all would lose ten platforms to a type they never touch.
 * The headless module exposes [KmpSupabaseAuthClient.raw] instead, and this extension bridges it.
 *
 * Prefer [rememberKmpSupabaseGoogleSignIn] / [rememberKmpSupabaseAppleSignIn] — they handle the flow, the result mapping
 * and the Android success-callback quirk. Reach for this only when you need something the wrappers
 * do not cover, such as driving `rememberSignInWithGoogle` with a custom `onIdToken`.
 */
public val KmpSupabaseAuthClient.composeAuth: ComposeAuth?
    get() = raw?.composeAuth
