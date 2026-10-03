package io.github.mobilebytelabs.supabaseauth

/**
 * iOS: NO. The native flow needs the GoogleSignIn SDK, which only the app's own Xcode/SPM graph can
 * supply — this library cannot. Google routes through [launchWebOAuth] instead.
 */
internal actual val googleNativeSupported: Boolean = false
