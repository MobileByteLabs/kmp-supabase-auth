package io.github.mobilebytelabs.supabaseauth.compose

import io.github.jan.supabase.CurrentPlatformTarget
import io.github.jan.supabase.PlatformTarget
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthConfig
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthProvider

/** Which flow a provider will actually take on this platform with this configuration. */
public enum class KmpSupabaseSignInPath {
    /** The OS-native sheet: Credential Manager on Android, ASAuthorization / Google SDK on iOS. */
    NATIVE,

    /** GoTrue's OAuth redirect through a browser — the external Safari app on iOS. */
    WEB_FALLBACK,
}

/** The resolved path for one provider, with the reason it resolved that way. */
public data class KmpSupabaseProviderSignInPath(
    public val provider: KmpSupabaseAuthProvider,
    public val path: KmpSupabaseSignInPath,
    public val reason: String,
)

/**
 * What [kmpSupabaseComposeAuthExtras] will actually do, per provider, on this platform.
 *
 * @property platform the target this was evaluated for
 * @property google resolved path for Google
 * @property apple resolved path for Apple
 */
public data class KmpSupabaseSignInPathReport(
    public val platform: PlatformTarget,
    public val google: KmpSupabaseProviderSignInPath,
    public val apple: KmpSupabaseProviderSignInPath,
) {
    /** Providers that will open a browser. Empty means every provider resolved to native. */
    public val webFallbacks: List<KmpSupabaseProviderSignInPath>
        get() = listOf(google, apple).filter { it.path == KmpSupabaseSignInPath.WEB_FALLBACK }

    /** One line per provider, suitable for a log statement at startup. */
    public fun format(): String = buildString {
        append("KmpSupabaseAuth sign-in paths on ").append(platform).append(':')
        listOf(google, apple).forEach {
            append("\n  ").append(it.provider).append(" -> ").append(it.path)
                .append(" (").append(it.reason).append(')')
        }
    }
}

/**
 * Reports which sign-in path each provider resolves to, WITHOUT starting a flow.
 *
 * Why this exists: a missing `googleWebClientId` does not fail. `kmpSupabaseComposeAuthExtras`
 * skips `googleNativeLogin(...)` when it is blank, ComposeAuth then sees a null
 * `googleLoginConfig` and silently calls its fallback, and the user is signed in through the
 * external Safari app instead of the native sheet. Everything looks fine — which is how an app
 * reaches App Review showing a web login it did not intend to ship.
 *
 * Call this at startup and log [KmpSupabaseSignInPathReport.format], or assert on [KmpSupabaseSignInPathReport.webFallbacks]
 * in a test, so an intended-native provider that quietly degraded is visible before release
 * rather than after.
 *
 * The logic mirrors [kmpSupabaseComposeAuthExtras] and ComposeAuth's own platform gates
 * (`config.googleLoginConfig != null` on both Android and iOS; Apple native is iOS-only).
 * Pass [googleNative]/[appleNative] the same values you passed there.
 *
 * One caveat this cannot see: native Google on iOS additionally requires the iOS app to link
 * `GoogleSignIn-iOS` via SPM. That is a link-time dependency, so if it were missing the app
 * would not have started — reaching this code at all means it is present.
 */
public fun diagnoseKmpSupabaseSignInPaths(
    config: KmpSupabaseAuthConfig,
    googleNative: Boolean = true,
    appleNative: Boolean = true,
    platform: PlatformTarget = CurrentPlatformTarget,
): KmpSupabaseSignInPathReport = KmpSupabaseSignInPathReport(
    platform = platform,
    google = resolveGoogle(config, googleNative, platform),
    apple = resolveApple(appleNative, platform),
)

private fun resolveGoogle(
    config: KmpSupabaseAuthConfig,
    googleNative: Boolean,
    platform: PlatformTarget,
): KmpSupabaseProviderSignInPath {
    val path: KmpSupabaseSignInPath
    val reason: String
    when {
        !googleNative -> {
            path = KmpSupabaseSignInPath.WEB_FALLBACK
            reason = "googleNative = false was passed to kmpSupabaseComposeAuthExtras"
        }

        !config.hasGoogleNative -> {
            path = KmpSupabaseSignInPath.WEB_FALLBACK
            reason = "googleWebClientId is blank, so googleNativeLogin() is never installed — " +
                "set it to the WEB client id (not the Android or iOS one)"
        }

        platform == PlatformTarget.ANDROID -> {
            path = KmpSupabaseSignInPath.NATIVE
            reason = "Credential Manager"
        }

        platform == PlatformTarget.IOS -> {
            path = KmpSupabaseSignInPath.NATIVE
            reason = "GoogleSignIn SDK via ComposeAuth's native bridge"
        }

        else -> {
            path = KmpSupabaseSignInPath.WEB_FALLBACK
            reason = "ComposeAuth implements native Google on Android and iOS only"
        }
    }
    return KmpSupabaseProviderSignInPath(KmpSupabaseAuthProvider.GOOGLE, path, reason)
}

private fun resolveApple(appleNative: Boolean, platform: PlatformTarget): KmpSupabaseProviderSignInPath {
    val path: KmpSupabaseSignInPath
    val reason: String
    when {
        !appleNative -> {
            path = KmpSupabaseSignInPath.WEB_FALLBACK
            reason = "appleNative = false was passed to kmpSupabaseComposeAuthExtras"
        }

        platform == PlatformTarget.IOS -> {
            path = KmpSupabaseSignInPath.NATIVE
            reason = "ASAuthorizationController"
        }

        else -> {
            path = KmpSupabaseSignInPath.WEB_FALLBACK
            reason = "Apple provides no native sign-in SDK outside iOS"
        }
    }
    return KmpSupabaseProviderSignInPath(KmpSupabaseAuthProvider.APPLE, path, reason)
}
