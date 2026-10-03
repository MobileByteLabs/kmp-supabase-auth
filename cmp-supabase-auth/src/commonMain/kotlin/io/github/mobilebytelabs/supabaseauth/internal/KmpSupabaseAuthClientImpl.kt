package io.github.mobilebytelabs.supabaseauth.internal

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthClient
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthOptions
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthUser
import io.github.mobilebytelabs.supabaseauth.googleNativeSupported
import io.github.mobilebytelabs.supabaseauth.launchWebOAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

internal class KmpSupabaseAuthClientImpl(
    private val client: SupabaseClient,
    private val options: KmpSupabaseAuthOptions,
    override val isConfigured: Boolean = true,
    /**
     * The app's own callback URL. Needed because the iOS in-app flow matches it as
     * `ASWebAuthenticationSession`'s `callbackURLScheme`; a wrong or absent value means the sheet
     * opens and then never completes, which is indistinguishable from the user stalling.
     */
    private val redirectUrl: String? = null,
) : KmpSupabaseAuthClient {

    override val raw: SupabaseClient?
        get() = if (isConfigured) client else null

    override val sessionStatus: Flow<SessionStatus>?
        get() = if (isConfigured) client.auth.sessionStatus else null

    override val currentUser: Flow<KmpSupabaseAuthUser?>
        get() = if (!isConfigured) {
            flowOf(null)
        } else {
            client.auth.sessionStatus
                .map { status -> status.toAuthUser() }
                .onEach { options.onSessionChanged?.invoke(it) }
        }

    override val isSignedIn: Flow<Boolean>
        get() = if (isConfigured) {
            client.auth.sessionStatus.map { it is SessionStatus.Authenticated }
        } else {
            flowOf(false)
        }

    private fun SessionStatus.toAuthUser(): KmpSupabaseAuthUser? {
        if (this !is SessionStatus.Authenticated) return null
        // A freshly-minted session's `user` is often NOT inflated — especially right after the
        // native id_token exchange. Email and identity metadata land on the client's cached
        // current user. Without this fallback a signed-in person renders with a blank name and
        // no email, which looks like a bug in the app rather than a race in the SDK.
        val raw = session.user ?: client.auth.currentUserOrNull() ?: return null
        // GoTrue's OWN flag, not an inference. The previous expression was
        // `raw.appMetadata?.let { providerFrom(it, false) } == null && email.isBlank && phone.isBlank`,
        // which could never be true: `providerFrom` always returns a provider (OTHER at worst), so
        // the first clause only held when appMetadata was null — and an anonymous GoTrue user HAS
        // app_metadata. Every anonymous session was therefore reported as NOT anonymous, which made
        // `isGuest` false, `isAuthenticated` true, and a guest render as a signed-in account.
        //
        // Verified on device 2026-10-01: three anonymous users existed server-side
        // (`select is_anonymous from auth.users` → true) while Settings showed "Signed in".
        //
        // The heuristic survives only as a fallback for a GoTrue that omits the field.
        val anonymous = raw.isAnonymous
            ?: (raw.email.isNullOrBlank() && raw.phone.isNullOrBlank())
        val mapped = mapAuthUser(
            id = raw.id,
            email = raw.email,
            metadata = raw.userMetadata,
            provider = providerFrom(raw.appMetadata, anonymous),
            isAnonymous = anonymous,
        )
        return options.userMapper?.invoke(mapped) ?: mapped
    }

    override suspend fun signInAnonymously(): Result<Unit> = guarded {
        client.auth.signInAnonymously()
    }

    // Both route through `launchWebOAuth`, NOT `auth.signInWith`, because on iOS signInWith opens
    // the EXTERNAL browser — the Guideline 4 rejection. The iOS actual presents
    // ASWebAuthenticationSession in-app; every other platform's actual delegates straight back to
    // signInWith (and Android is already in-app via Custom Tabs).
    override val supportsNativeGoogle: Boolean = googleNativeSupported

    override suspend fun signInWithAppleFallback(): Result<Unit> =
        guarded { client.launchWebOAuth(KmpSupabaseAuthProvider.APPLE, redirectUrl).getOrThrow() }

    override suspend fun signInWithGoogleFallback(): Result<Unit> =
        guarded { client.launchWebOAuth(KmpSupabaseAuthProvider.GOOGLE, redirectUrl).getOrThrow() }

    override suspend fun hasRestorableSession(): Boolean =
        isConfigured && runCatching { client.auth.currentSessionOrNull() != null }.getOrDefault(false)

    override fun currentAccessToken(): String? =
        runCatching { client.auth.currentSessionOrNull()?.accessToken }.getOrNull()

    override suspend fun signOut(): Result<Unit> = guarded { client.auth.signOut() }

    private inline fun guarded(block: () -> Unit): Result<Unit> =
        if (!isConfigured) Result.success(Unit) else runCatching { block() }
}
