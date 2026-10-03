package io.github.mobilebytelabs.supabaseauth

/**
 * Is this a DEBUG build of the host app?
 *
 * Drives [KmpSupabaseAuthLog]'s default: diagnostics ON in debug, OFF in release, with nothing for
 * a consumer to wire. Before this, logging defaulted to off everywhere and every app had to opt in
 * — and because none did, an iOS sign-in failure produced a device log with ZERO lines about the
 * sign-in path, four device rounds in a row. Auth is the part of an app least observable from the
 * outside; it is the wrong place to make visibility opt-in.
 *
 * Deliberately NOT a flavor check. `prodDebug` vs `prodRelease` is Android/Gradle vocabulary; this
 * is the platform's own notion of a debug binary, so it answers correctly on iOS, desktop and web
 * too — including fork flavor names this library has never heard of.
 */
internal expect val isDebugBuild: Boolean
