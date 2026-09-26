package io.github.mobilebytelabs.supabaseauth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Holds the current session for synchronous reads.
 *
 * **Deliberately a plain StateFlow holder, not a Store5 store.** Three reasons, all learned the
 * hard way elsewhere:
 *
 *  - **Memory only.** GoTrue already persists and refreshes the session. Caching it again here
 *    would let the app show a signed-in user after the real token had expired.
 *  - **Not enrolled in the logout purge.** This store is what *tells* the app a logout happened;
 *    purging it would clear the very stream the app reads to notice the purge.
 *  - **Push, not fetch.** GoTrue emits session changes; there is no fetcher to write. Adding one
 *    would re-derive state that is already being pushed, which is a second read path for one
 *    piece of state.
 */
public interface AuthSessionStore {
    public val user: StateFlow<AuthUser?>
    public val isSignedIn: StateFlow<Boolean>

    /** Begin mirroring the client's session. Call once, eagerly, at graph construction. */
    public fun start(scope: CoroutineScope)

    /** Sign out and drop local state. */
    public suspend fun clear()
}

internal class DefaultAuthSessionStore(private val client: SupabaseAuthClient) : AuthSessionStore {

    private val _user = MutableStateFlow<AuthUser?>(null)
    override val user: StateFlow<AuthUser?> = _user.asStateFlow()

    private val _isSignedIn = MutableStateFlow(false)
    override val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    override fun start(scope: CoroutineScope) {
        client.currentUser
            .onEach { value ->
                _user.value = value
                _isSignedIn.value = value != null
            }
            .launchIn(scope)
    }

    override suspend fun clear() {
        // Result intentionally not unwrapped. A failed remote sign-out must STILL clear local
        // state: leaving a session behind shows a logged-in UI holding a token the server has
        // already rejected, which is strictly worse than a local sign-out the server has not
        // yet seen.
        client.signOut()
        _user.value = null
        _isSignedIn.value = false
    }
}
