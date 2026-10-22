package com.blockade.quoridor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.model.CommunityStats
import com.blockade.quoridor.data.repository.CommunityRepository
import com.blockade.quoridor.data.repository.GameException
import com.blockade.quoridor.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = false,
    val joinCode: String = "",
    val error: String? = null,
    val resume: ResumeTarget? = null,
    /** Null until the first load succeeds (or while offline): the counters are simply not shown. */
    val community: CommunityStats? = null,
)

/** A room this device was in when the app last closed. */
data class ResumeTarget(val roomId: String, val code: String, val playerIndex: Int, val started: Boolean)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: GameRepository,
    private val community: CommunityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val roomId = repo.activeRoomId.first() ?: return@launch
            runCatching { repo.getRoom(roomId) }
                .onSuccess { r ->
                    val over = r.match?.state?.get("status")?.jsonPrimitive?.content == "finished"
                    if (over) repo.clearRoom()
                    else _state.update {
                        it.copy(resume = ResumeTarget(r.roomId, r.code, r.playerIndex, r.match != null))
                    }
                }
                // Room no longer exists or we were removed from it; only forget it on a definite server answer.
                .onFailure { e -> if (e is GameException && e.status != null) repo.clearRoom() }
        }
    }

    /** Refreshes "N online / X joined". Keeps the last numbers if the request fails. */
    suspend fun refreshCommunity() {
        runCatching { community.stats() }.onSuccess { stats -> _state.update { it.copy(community = stats) } }
    }

    fun dismissResume() {
        _state.update { it.copy(resume = null) }
        viewModelScope.launch { repo.clearRoom() }
    }

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
