package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * This library's boundary onto Supabase GoTrue, over the consumer's ONE shared client.
 *
 * Headless by construction: this module carries NO Compose. The ComposeAuth plugin — and with it
 * `rememberSignInWithGoogle` / `rememberSignInWithApple` — lives in `cmp-supabase-auth-compose`,
 * because `compose-auth` publishes only 7 targets and depending on it here would collapse this
 * module from 17 to 7. [raw] is the seam that module uses to reach the underlying client.
 */
public interface SupabaseAuthClient {

    /** True when real (non-placeholder) Supabase credentials are present. */
    public val isConfigured: Boolean

    /**
     * The underlying Supabase client.
     *
     * Exposed for `cmp-supabase-auth-compose`, which needs it to reach the ComposeAuth plugin.
     * Application code should prefer [currentUser] / [isSignedIn] and never touch this.
     */
    public val raw: SupabaseClient?

    /**
     * Raw GoTrue session stream; null when unconfigured.
     *
     * **This is the source of truth for a successful sign-in — not a button's onResult callback.**
     * On Android the native-Google `onResult(Success)` callback frequently never fires even though
     * the id_token exchange succeeded and the session landed. A UI keyed on the callback spins
     * forever while the user is, in fact, signed in.
     */
    public val sessionStatus: Flow<SessionStatus>?

    /** The signed-in user, or null while signed out. Constant null when unconfigured. */
    public val currentUser: Flow<AuthUser?>

    /** True while a session exists. Constant false when unconfigured. */
    public val isSignedIn: Flow<Boolean>

    /** Create an anonymous session. RLS applies immediately; `is_anonymous` is true. */
    public suspend fun signInAnonymously(): Result<Unit>

    /** Web-OAuth Apple sign-in, for platforms with no native provider (incl. macOS, JVM, web). */
    public suspend fun signInWithAppleFallback(): Result<Unit>

    /** Web-OAuth Google sign-in, for platforms with no native provider. */
    public suspend fun signInWithGoogleFallback(): Result<Unit>

    /** True when GoTrue holds a persisted, restorable session. Call at cold start. */
    public suspend fun hasRestorableSession(): Boolean

    /** Current access token, or null. */
    public fun currentAccessToken(): String?

    /** Clear the GoTrue session. */
    public suspend fun signOut(): Result<Unit>
}
