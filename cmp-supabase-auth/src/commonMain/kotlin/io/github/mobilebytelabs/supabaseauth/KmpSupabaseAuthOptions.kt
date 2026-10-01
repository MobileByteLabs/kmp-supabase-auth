package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClientBuilder

/**
 * Extension hooks. Every one is optional; the defaults are the supported happy path.
 *
 * [extraInstall] exists so a consumer needing Realtime or Storage installs them on the SAME
 * client rather than building a second one — a second client carries no session, so every
 * RLS-gated call resolves no `auth.uid()` while compiling cleanly.
 */
public class KmpSupabaseAuthOptions internal constructor() {

    internal var extraInstall: SupabaseClientBuilder.() -> Unit = {}
        private set
    internal var userMapper: ((KmpSupabaseAuthUser) -> KmpSupabaseAuthUser)? = null
        private set
    internal var onSessionChanged: ((KmpSupabaseAuthUser?) -> Unit)? = null
        private set
    internal var googleNativeEnabled: Boolean = true
        private set
    internal var appleNativeEnabled: Boolean = true
        private set

    /** Install further Supabase modules on the same client. */
    public fun extraInstall(block: SupabaseClientBuilder.() -> Unit) {
        extraInstall = block
    }

    /** Post-process the mapped user — e.g. to derive a display name differently. */
    public fun userMapper(block: (KmpSupabaseAuthUser) -> KmpSupabaseAuthUser) {
        userMapper = block
    }

    /** Observe session transitions — e.g. to set an analytics user id. */
    public fun onSessionChanged(block: (KmpSupabaseAuthUser?) -> Unit) {
        onSessionChanged = block
    }

    /** Turn off the native Android Google flow, forcing the OAuth redirect. */
    public fun googleNative(enabled: Boolean) {
        googleNativeEnabled = enabled
    }

    /** Turn off the native iOS Apple flow, forcing the OAuth redirect. */
    public fun appleNative(enabled: Boolean) {
        appleNativeEnabled = enabled
    }
}

internal fun buildOptions(configure: KmpSupabaseAuthOptions.() -> Unit): KmpSupabaseAuthOptions =
    KmpSupabaseAuthOptions().apply(configure)
