package io.github.mobilebytelabs.supabaseauth

/**
 * Suspends until the host app comes back to the foreground, then returns.
 *
 * **Why the library needs this.** A provider that is CANCELLED reports nothing at all — no success,
 * no error, no cancellation. MEASURED on `mbs/cappy`, Android 15, 2026-10-03:
 *
 * ```
 * 12:18:12.8  Custom Tab window hidden               (the person dismissed it)
 * 12:18:13.0  CustomTabActivity DESTROYED, removed from the app's task
 * 12:20:35.4  [supabase-auth] GOOGLE: TIMEOUT after 1m — the provider never called back
 * ```
 *
 * So for over two minutes the screen sat on "Signing in…" with every button disabled, and the only
 * thing that eventually freed it was the 60-second no-response watchdog — which reports
 * [KmpSupabaseAuthError.NoResponse], a DIFFERENT failure, with a message about signing
 * certificates. A person who simply changed their mind got a spinner and then a wrong error.
 *
 * Returning to the foreground with no session is the one signal the provider cannot withhold: the
 * sheet or tab is gone and the app is interactive again. Pairing it with a short grace period (to
 * let a genuine late callback win the race) turns "nothing happened" into
 * [KmpSupabaseAuthError.Cancelled] in about a second instead of a minute.
 *
 * Deliberately NOT built on `androidx.lifecycle`'s compose integration: that would add a dependency
 * to all seven targets of this module to obtain a signal the library can already see — it holds the
 * Android `Application` (captured by `KmpSupabaseAuthInitProvider`) and can use
 * `NSNotificationCenter` on Apple platforms.
 *
 * **Public only because `internal` is module-scoped.** This is platform PLUMBING consumed by
 * `cmp-supabase-auth-compose`'s sign-in launchers, which live in a different Gradle module — the
 * same reason `KmpSupabaseAuthClient.supportsNativeGoogle` is public. Consumers have no reason to
 * call it; the launchers already race it for you.
 *
 * Implementations never emit for the CURRENT foreground state — only for a genuine
 * background → foreground transition after this call starts suspending. A platform with no such
 * notion (desktop, JS, Wasm) suspends forever, which correctly disables cancel-detection there and
 * leaves the watchdog as the only net.
 */
public expect suspend fun awaitAppForegroundReturn()
