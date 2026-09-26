package io.github.mobilebytelabs.supabaseauth

/** Which provider established the current session. */
public enum class AuthProvider { GOOGLE, APPLE, ANONYMOUS, EMAIL, OTHER }

/**
 * Provider-neutral identity. Identity ONLY — no app profile data.
 *
 * This library owns the session; the consuming app keeps owning user profile data in its own
 * store. Adding a profile field here would make the library a second owner of state the app
 * already owns, which is the failure mode the ownership boundary exists to prevent.
 *
 * Deliberately free of Supabase types so a feature module can depend on this without pulling
 * `auth-kt` onto its compile path.
 */
public data class AuthUser(
    val id: String,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val provider: AuthProvider = AuthProvider.OTHER,
    val isAnonymous: Boolean = false,
)
