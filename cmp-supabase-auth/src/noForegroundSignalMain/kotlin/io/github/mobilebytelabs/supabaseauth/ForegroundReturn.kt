package io.github.mobilebytelabs.supabaseauth

import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Desktop, JS, Wasm, and the non-iOS Apple leaves: suspend forever, never emitting.
 *
 * Honest rather than approximate. Cancel-detection here would have to guess at a
 * background → foreground transition these targets either lack (JS/Wasm, where the OAuth leg is a
 * same-tab redirect or popup) or express differently enough that a wrong guess would report
 * [KmpSupabaseAuthError.Cancelled] for a sign-in still in progress — strictly worse than the
 * watchdog it would be replacing.
 *
 * macOS and the other Apple leaves DO have `NSApplicationDidBecomeActive`; they are grouped here
 * because none of them is a shipped target for this flow yet, and an untested foreground signal is
 * not an improvement over a working timeout. Moving one out is adding its own actual, nothing more.
 */
public actual suspend fun awaitAppForegroundReturn(): Unit = suspendCancellableCoroutine { }
