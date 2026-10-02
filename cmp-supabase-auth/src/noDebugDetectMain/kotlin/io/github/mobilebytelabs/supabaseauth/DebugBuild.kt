package io.github.mobilebytelabs.supabaseauth

/**
 * JVM/desktop, JS and Wasm have no portable notion of a debug binary, so they answer false and
 * diagnostics stay opt-in there via `KmpSupabaseAuthLog.handler`.
 *
 * `-ea` (desiredAssertionStatus) was considered for the JVM and rejected: it is off by default in
 * every normal desktop run, so it would report false for genuine debug sessions while adding a
 * second, differently-wrong answer to maintain.
 */
internal actual val isDebugBuild: Boolean = false
