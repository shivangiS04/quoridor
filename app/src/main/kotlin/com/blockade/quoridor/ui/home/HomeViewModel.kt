package com.blockade.quoridor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = false,
    val joinCode: String = "",
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: GameRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    fun setJoinCode(code: String) = _state.update { it.copy(joinCode = code.uppercase().take(8)) }

    fun hostGame(onCreated: (roomId: String, code: String) -> Unit) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.createRoom() }
            .onSuccess { r ->
                repo.saveRoom(r.roomId)
                _state.update { it.copy(loading = false) }
                onCreated(r.roomId, r.code)
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }

    fun joinGame(onJoined: (roomId: String, playerIndex: Int) -> Unit) = viewModelScope.launch {
        val code = _state.value.joinCode.trim()
        if (code.length != 8) { _state.update { it.copy(error = "Enter the 8-character room code") }; return@launch }
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.joinRoom(code) }
            .onSuccess { r ->
                repo.saveRoom(r.roomId)
                _state.update { it.copy(loading = false) }
                onJoined(r.roomId, r.playerIndex)
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
    }
}
