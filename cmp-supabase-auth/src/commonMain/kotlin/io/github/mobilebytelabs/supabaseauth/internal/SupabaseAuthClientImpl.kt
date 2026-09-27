package io.github.mobilebytelabs.supabaseauth.internal

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.mobilebytelabs.supabaseauth.AuthUser
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthClient
import io.github.mobilebytelabs.supabaseauth.SupabaseAuthOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

internal class SupabaseAuthClientImpl(
    private val client: SupabaseClient,
    private val options: SupabaseAuthOptions,
    override val isConfigured: Boolean = true,
) : SupabaseAuthClient {

    override val raw: SupabaseClient?
        get() = if (isConfigured) client else null

    override val sessionStatus: Flow<SessionStatus>?
        get() = if (isConfigured) client.auth.sessionStatus else null

    override val currentUser: Flow<AuthUser?>
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

    private fun SessionStatus.toAuthUser(): AuthUser? {
        if (this !is SessionStatus.Authenticated) return null
        // A freshly-minted session's `user` is often NOT inflated — especially right after the
        // native id_token exchange. Email and identity metadata land on the client's cached
        // current user. Without this fallback a signed-in person renders with a blank name and
        // no email, which looks like a bug in the app rather than a race in the SDK.
        val raw = session.user ?: client.auth.currentUserOrNull() ?: return null
        val anonymous = raw.appMetadata?.let { providerFrom(it, false) } == null &&
            raw.email.isNullOrBlank() && raw.phone.isNullOrBlank()
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

    override suspend fun signInWithAppleFallback(): Result<Unit> = guarded {
        client.auth.signInWith(Apple)
    }

    override suspend fun signInWithGoogleFallback(): Result<Unit> = guarded {
        client.auth.signInWith(Google)
    }

    override suspend fun hasRestorableSession(): Boolean =
        isConfigured && runCatching { client.auth.currentSessionOrNull() != null }.getOrDefault(false)

    override fun currentAccessToken(): String? =
        runCatching { client.auth.currentSessionOrNull()?.accessToken }.getOrNull()

    override suspend fun signOut(): Result<Unit> = guarded { client.auth.signOut() }

    private inline fun guarded(block: () -> Unit): Result<Unit> =
        if (!isConfigured) Result.success(Unit) else runCatching { block() }
}
