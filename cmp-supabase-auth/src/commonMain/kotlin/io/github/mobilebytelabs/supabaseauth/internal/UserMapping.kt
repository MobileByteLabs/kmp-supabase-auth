package io.github.mobilebytelabs.supabaseauth.internal

import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthUser
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps raw GoTrue identity fields to [KmpSupabaseAuthUser].
 *
 * Blank strings become null throughout: GoTrue returns `""` rather than omitting a key, and an
 * empty display name rendered in a UI reads as a bug rather than as absent data.
 */
internal fun mapAuthUser(
    id: String,
    email: String?,
    metadata: JsonObject?,
    provider: KmpSupabaseAuthProvider,
    isAnonymous: Boolean,
): KmpSupabaseAuthUser {
    fun meta(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        metadata?.get(key)?.jsonPrimitive?.contentOrNull?.ifBlank { null }
    }
    return KmpSupabaseAuthUser(
        id = id,
        email = email?.ifBlank { null },
        displayName = meta("full_name", "name", "preferred_username"),
        avatarUrl = meta("avatar_url", "picture"),
        provider = provider,
        isAnonymous = isAnonymous,
    )
}

/** Derives the provider from GoTrue's `app_metadata.provider`, falling back to shape. */
internal fun providerFrom(appMetadata: JsonObject?, isAnonymous: Boolean): KmpSupabaseAuthProvider = when {
    isAnonymous -> KmpSupabaseAuthProvider.ANONYMOUS

    else -> when (appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull) {
        "google" -> KmpSupabaseAuthProvider.GOOGLE
        "apple" -> KmpSupabaseAuthProvider.APPLE
        "email" -> KmpSupabaseAuthProvider.EMAIL
        else -> KmpSupabaseAuthProvider.OTHER
    }
}
