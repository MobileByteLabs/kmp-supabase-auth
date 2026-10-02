package io.github.mobilebytelabs.supabaseauth

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Kotlin/Native (iOS, macOS, tvOS, watchOS, linux, mingw) answers this directly: `isDebugBinary` is
 * true for a framework linked with `-g`/debug, which is exactly what an app's Debug configuration
 * embeds. No Gradle flavor plumbing, and correct for an Xcode build this library never sees.
 */
@OptIn(ExperimentalNativeApi::class)
internal actual val isDebugBuild: Boolean = Platform.isDebugBinary
