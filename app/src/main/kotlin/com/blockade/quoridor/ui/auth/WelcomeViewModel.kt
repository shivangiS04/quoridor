package com.blockade.quoridor.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.repository.AuthRepository
import com.blockade.quoridor.data.repository.GameException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeUiState(val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class WelcomeViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(WelcomeUiState())
    val state = _state.asStateFlow()

    fun google() = run { auth.signInWithGoogle() }
    fun guest() = run { auth.continueAsGuest() }

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { WelcomeUiState(loading = true) }
        try {
            block()
            // Google returns through the deep link, which flips the session gate; guests flip it right away.
            _state.update { it.copy(loading = false) }
        } catch (e: GameException) {
            _state.update { WelcomeUiState(error = e.message) }
        }
    }
}
