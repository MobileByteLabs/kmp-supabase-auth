package io.github.mobilebytelabs.supabaseauth

/**
 * Can THIS platform run native Google sign-in using only what this library ships?
 *
 * This is a question about **delivery**, not about configuration — see
 * [KmpSupabaseAuthConfig.hasGoogleNative] for the configured half.
 *
 * - **Android → true.** `googleNativeLogin` resolves through Credential Manager, which is an
 *   AndroidX dependency the library declares and Gradle resolves transitively. Nothing is asked of
 *   the consumer.
 * - **iOS → false.** supabase-kt drives native iOS Google through a cinterop bridge
 *   (`compose-auth-iosArm64Cinterop-GoogleSignInNativeBridge`) that compiles against headers and
 *   **links nothing**: its klib's `libraryPaths`/`linkerOpts` point at the supabase-kt CI build
 *   directory (`/Users/runner/work/supabase-kt-plugins/…`) and it embeds no `.a` and no
 *   `.framework`. The Kotlin side builds, the app links, and the FIRST TAP aborts at runtime.
 *   Supplying the SDK requires an **SPM declaration in a package Xcode resolves**, and Xcode
 *   resolves the package graph BEFORE any Gradle task runs — so no Maven-published library can
 *   inject it. MEASURED 2026-10-02 (`mbs/cappy`, iPhone 13): Continue with Apple completed
 *   end-to-end while Continue with Google aborted.
 *
 * iOS therefore takes [launchWebOAuth] instead, which uses `ASWebAuthenticationSession` — a
 * **system** framework, nothing to add to the app — and is the API Apple's own reviewers named
 * when they rejected the external-browser flow under Guideline 4. Native Apple needs no equivalent
 * switch: `appleNativeLogin` resolves through `AuthenticationServices`, also a system framework,
 * which is why Apple worked on iOS while Google did not.
 *
 * The capability this trades away is the native account picker. The SDK's own transport is AppAuth,
 * which is itself `ASWebAuthenticationSession`, so the browser is the same either way — what the
 * SDK adds is reading Google accounts already on the device. Safari shares Google's cookies, so a
 * signed-in person is still one tap; a signed-out one types credentials.
 */
internal expect val googleNativeSupported: Boolean
