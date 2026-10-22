package com.blockade.quoridor.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the app should show first. */
enum class AuthGate { LOADING, SIGNED_OUT, SIGNED_IN }

@HiltViewModel
class SessionViewModel @Inject constructor(auth: AuthRepository) : ViewModel() {
    val gate: StateFlow<AuthGate> = auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> AuthGate.SIGNED_IN
                is SessionStatus.NotAuthenticated -> AuthGate.SIGNED_OUT
                // A saved session that could not be refreshed (offline): let the player in; calls report the error.
                is SessionStatus.NetworkError -> AuthGate.SIGNED_IN
                else -> AuthGate.LOADING
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthGate.LOADING)
}
