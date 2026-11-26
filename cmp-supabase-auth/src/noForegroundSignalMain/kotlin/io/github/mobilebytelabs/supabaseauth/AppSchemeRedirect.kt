package io.github.mobilebytelabs.supabaseauth

/** Desktop, JS, Wasm and the non-iOS Apple leaves have no app-scheme receiver. */
internal actual val deliversAppSchemeRedirect: Boolean = false
