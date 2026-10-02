package io.github.mobilebytelabs.supabaseauth

/**
 * Android: YES — Credential Manager is a declared dependency, resolved transitively.
 *
 * Desktop/JS/Wasm also land here and have no native flow at all; for them `hasGoogleNative` is
 * decided by supabase-kt, which falls back to the OAuth redirect regardless. Answering `true` here
 * costs them nothing, where answering `false` would route them through [launchWebOAuth]'s default
 * actual to reach the same redirect by a longer path.
 */
internal actual val googleNativeSupported: Boolean = true
