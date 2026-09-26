package io.github.mobilebytelabs.supabaseauth.compose

import io.github.mobilebytelabs.supabaseauth.AuthError
import io.github.mobilebytelabs.supabaseauth.AuthProvider
import io.github.mobilebytelabs.supabaseauth.AuthRepository
import io.github.mobilebytelabs.supabaseauth.AuthUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

public data class SupabaseAuthUiState(
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val isSignedIn: Boolean = false,
    val error: AuthError? = null,
)

/**
 * Drives a login screen.
 *
 * **Signed-in is derived from the repository's session flow, never from a provider callback.**
 * That is not a style preference. On Android the native-Google success callback frequently never
 * fires even though the exchange succeeded, so a callback-driven ViewModel leaves the person on a
 * spinner while they are in fact authenticated. This is the single most load-bearing behaviour in
 * the library, and `SupabaseAuthViewModelTest` pins it by reaching signed-in with no callback at
 * all.
 */
public class SupabaseAuthViewModel(private val repository: AuthRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SupabaseAuthUiState())
    public val state: StateFlow<SupabaseAuthUiState> = _state.asStateFlow()

    init {
        repository.currentUser
            .onEach { user ->
                _state.value = _state.value.copy(
                    user = user,
                    isSignedIn = user != null,
                    // A landed session ends the loading state regardless of any callback.
                    isLoading = if (user != null) false else _state.value.isLoading,
                )
            }
            .launchIn(scope)
    }

    /** Call immediately before launching a provider flow. */
    public fun onSignInStarted() {
        _state.value = _state.value.copy(isLoading = true, error = null)
    }

    /** Wire to a `remember*SignIn` launcher's `onError`. */
    public fun onSignInFailed(error: AuthError) {
        _state.value = _state.value.copy(
            isLoading = false,
            // Dismissing the provider sheet is not an error to show anyone.
            error = error.takeUnless { it is AuthError.Cancelled },
        )
    }

    public fun continueAsGuest() {
        onSignInStarted()
        scope.launch {
            repository.continueAsGuest()
                .onFailure { onSignInFailed(it as? AuthError ?: AuthError.Unknown(it)) }
        }
    }

    /** Web-OAuth sign-in, for platforms with no native provider. */
    public fun signInWithFallback(provider: AuthProvider) {
        onSignInStarted()
        scope.launch {
            repository.signInWithFallback(provider)
                .onFailure { onSignInFailed(it as? AuthError ?: AuthError.Unknown(it)) }
        }
    }

    public fun signOut() {
        scope.launch { repository.signOut() }
    }

    public fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
