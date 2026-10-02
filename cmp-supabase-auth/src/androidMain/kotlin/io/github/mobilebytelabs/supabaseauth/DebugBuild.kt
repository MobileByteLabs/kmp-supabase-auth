package io.github.mobilebytelabs.supabaseauth

import android.content.pm.ApplicationInfo

/**
 * Android reads the HOST APP's `FLAG_DEBUGGABLE`, not a `BuildConfig.DEBUG` of its own — a library's
 * own BuildConfig reports how the LIBRARY was built (always release, from Maven), which is never the
 * question being asked.
 *
 * The Application comes from [CurrentActivity], installed by `KmpSupabaseAuthInitProvider` at
 * process start — ContentProviders run before `Application.onCreate`, so this resolves early. If it
 * is somehow unavailable, answer false: silence is the safe default, never accidental logging in a
 * release build.
 */
internal actual val isDebugBuild: Boolean
    get() = CurrentActivity.application
        ?.applicationInfo
        ?.let { it.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
        ?: false
