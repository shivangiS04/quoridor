package com.blockade.quoridor.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.repository.GameRepository
import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.MatchSnapshot
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.domain.rules.QuoridorRules.Pos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class InteractionMode { MOVE, WALL }

data class WallPreview(val row: Int, val col: Int, val horizontal: Boolean)

data class GameUiState(
    val snapshot: MatchSnapshot? = null,
    val myPlayerIndex: Int = 0,
    val mode: InteractionMode = InteractionMode.MOVE,
    val wallHorizontal: Boolean = true,
    val wallPreview: WallPreview? = null,
    val selectedCell: Pos? = null,
    val validMoves: List<Pos> = emptyList(),
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val gameState: GameState? get() = snapshot?.state
    val isMyTurn: Boolean get() = gameState?.currentTurn == myPlayerIndex
    val finished: Boolean get() = gameState?.status == "finished"
    val winner: Int? get() = gameState?.winner
}

@HiltViewModel
class GameViewModel @Inject constructor(
    private val repo: GameRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state = _state.asStateFlow()

    fun init(roomId: String, myPlayerIndex: Int) {
        _state.update { it.copy(myPlayerIndex = myPlayerIndex) }
        viewModelScope.launch {
            runCatching { repo.getRoom(roomId) }.onSuccess { r ->
                if (r.match != null) {
                    val snap = MatchSnapshot(
                        state = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                            .decodeFromString(r.match.state.toString()),
                        revision = r.match.revision,
                    )
                    updateSnapshot(snap)
                }
            }
        }
        repo.observeMatch(roomId)
            .onEach { updateSnapshot(it) }
            .launchIn(viewModelScope)
    }

    private fun updateSnapshot(snap: MatchSnapshot) {
        _state.update { s ->
            val moves = if (snap.state.currentTurn == s.myPlayerIndex)
                QuoridorRules.getValidMoves(snap.state, s.myPlayerIndex)
            else emptyList()
            s.copy(snapshot = snap, validMoves = moves, wallPreview = null, selectedCell = null, error = null)
        }
    }

    fun toggleMode() = _state.update {
        it.copy(mode = if (it.mode == InteractionMode.MOVE) InteractionMode.WALL else InteractionMode.MOVE,
            wallPreview = null, selectedCell = null)
    }

    fun toggleWallOrientation() = _state.update {
        it.copy(wallHorizontal = !it.wallHorizontal, wallPreview = null)
    }

    fun onCellTap(roomId: String, row: Int, col: Int) {
        val s = _state.value
        if (!s.isMyTurn || s.submitting || s.finished) return
        if (s.mode == InteractionMode.MOVE) {
            if (s.validMoves.contains(Pos(row, col))) submitMove(roomId, row, col)
        }
    }

    fun onWallSlotTap(roomId: String, wr: Int, wc: Int) {
        val s = _state.value
        if (!s.isMyTurn || s.submitting || s.finished) return
        val gs = s.gameState ?: return
        val isValid = QuoridorRules.isWallValid(gs, wr, wc, s.wallHorizontal, s.myPlayerIndex)
        if (!isValid) { _state.update { it.copy(error = "Invalid wall placement") }; return }

        if (s.wallPreview?.row == wr && s.wallPreview.col == wc && s.wallPreview.horizontal == s.wallHorizontal) {
            // Second tap on same slot → confirm
            submitWall(roomId, wr, wc, s.wallHorizontal)
        } else {
            _state.update { it.copy(wallPreview = WallPreview(wr, wc, s.wallHorizontal), error = null) }
        }
    }

    private fun submitMove(roomId: String, row: Int, col: Int) = viewModelScope.launch {
        val rev = _state.value.snapshot?.revision ?: return@launch
        _state.update { it.copy(submitting = true) }
        runCatching { repo.submitMove(roomId, rev, row, col) }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        _state.update { it.copy(submitting = false) }
    }

    private fun submitWall(roomId: String, wr: Int, wc: Int, horizontal: Boolean) = viewModelScope.launch {
        val rev = _state.value.snapshot?.revision ?: return@launch
        _state.update { it.copy(submitting = true, wallPreview = null) }
        runCatching { repo.submitWall(roomId, rev, wr, wc, horizontal) }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        _state.update { it.copy(submitting = false) }
    }
}
