package io.github.mobilebytelabs.supabaseauth

/**
 * The library's single init surface, in the shape `cmp-firebase`'s `FirebaseKit` established:
 * one object a consumer touches to bring the library up, with setup living in the library rather
 * than being copy-pasted into every app.
 *
 * Nothing here talks to Supabase yet — [validate] exists so a consumer can fail fast on a
 * misconfigured setup before any network call. The client, session store and repository land on
 * top of this surface.
 */
public object SupabaseAuth {
    /**
     * Validate a config, failing fast on the one condition no fallback can rescue.
     *
     * Returns the config unchanged when usable, so it reads as a pass-through at the call site.
     * Throws [IllegalArgumentException] only for the one condition no fallback can rescue — an
     * absent or placeholder `projectRef`, without which there is no client to install onto.
     *
     * A missing provider id is deliberately NOT fatal: it degrades to the OAuth-redirect path,
     * and an app mid-way through console setup should still run.
     */
    public fun validate(config: SupabaseAuthConfig): SupabaseAuthConfig {
        require(config.isConfigured) {
            "SupabaseAuthConfig.projectRef is blank or still a placeholder (\"${config.projectRef}\"). " +
                "Set it to your Supabase project ref — the library installs onto that project's " +
                "existing client and cannot create one."
        }

        return config
    }
}
