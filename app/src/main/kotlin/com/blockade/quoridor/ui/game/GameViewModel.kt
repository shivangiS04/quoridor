package com.blockade.quoridor.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockade.quoridor.data.repository.GameException
import com.blockade.quoridor.data.repository.GameRepository
import com.blockade.quoridor.domain.ai.AiDifficulty
import com.blockade.quoridor.domain.ai.QuoridorAi
import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.MatchSnapshot
import com.blockade.quoridor.domain.rules.MoveHistory
import com.blockade.quoridor.domain.rules.MoveRecord
import com.blockade.quoridor.domain.rules.PlacedWall
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.domain.rules.QuoridorRules.Pos
import com.blockade.quoridor.ui.sound.Sfx
import com.blockade.quoridor.ui.sound.SoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class InteractionMode { MOVE, WALL }

data class WallPreview(val row: Int, val col: Int, val horizontal: Boolean)

/** Presentation signal: the player tried an illegal wall here; [tick] changes on every attempt so the UI can re-animate. */
data class InvalidFlash(val row: Int, val col: Int, val horizontal: Boolean, val tick: Int)

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
    val nowMs: Long = System.currentTimeMillis(),
    val confirmResign: Boolean = false,
    val history: List<MoveRecord> = emptyList(),
    val soundOn: Boolean = true,
    // Presentation-only state (HUD labels, win-screen stats, invalid-placement feedback).
    val vsComputer: Boolean = false,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val invalidFlash: InvalidFlash? = null,
) {
    /** Who placed each wall we have seen this session (the match state itself doesn't record owners). */
    val wallOwners: Map<PlacedWall, Int> get() =
        history.mapNotNull { r -> r.wall?.let { it to r.player } }.toMap()
    val wallsUsed: Int get() = (QuoridorRules.WALLS_PER_PLAYER - (gameState?.players?.getOrNull(myPlayerIndex)?.wallsLeft
        ?: QuoridorRules.WALLS_PER_PLAYER))
    val myTurnsTaken: Int get() = history.count { it.player == myPlayerIndex && !it.gameEnd }
    val elapsedSeconds: Long get() {
        val start = startedAtMs ?: return 0
        return ((endedAtMs ?: nowMs) - start).coerceAtLeast(0) / 1000
    }
    /** The most recent wall placement, so the board can animate it in. */
    val lastWall: PlacedWall? get() = history.lastOrNull()?.wall
    /** Seconds before the player on turn forfeits, or null when no timer applies. */
    val secondsLeft: Long? get() {
        val last = snapshot?.updatedAtMs ?: return null
        if (gameState?.status != "playing") return null
        val left = QuoridorRules.TURN_TIMEOUT_MS - (nowMs - last)
        return (left.coerceAtLeast(0) + 999) / 1000
    }
    val canClaimTimeout: Boolean get() = !isMyTurn && gameState?.status == "playing" && secondsLeft == 0L
    val gameState: GameState? get() = snapshot?.state
    val isMyTurn: Boolean get() = gameState?.currentTurn == myPlayerIndex
    val finished: Boolean get() = gameState?.status == "finished"
    val winner: Int? get() = gameState?.winner
}

@HiltViewModel
class GameViewModel @Inject constructor(
    private val repo: GameRepository,
    private val sound: SoundPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state = _state.asStateFlow()

    private var roomId: String? = null

    // Single-player: set when playing the computer; the human is always player 0 and moves first.
    private var ai: QuoridorAi? = null
    private val isLocal get() = ai != null

    fun initLocal(difficulty: AiDifficulty) {
        if (ai != null) return
        ai = QuoridorAi(difficulty)
        _state.update { it.copy(myPlayerIndex = 0, vsComputer = true) }
        repo.soundEnabled.onEach { on -> _state.update { it.copy(soundOn = on) } }.launchIn(viewModelScope)
        updateSnapshot(MatchSnapshot(QuoridorRules.initialState(firstTurn = 0), revision = 0))
    }

    /** Starts a fresh game against the same computer opponent (the "Play Again" button). */
    fun restartLocal() {
        if (!isLocal) return
        _state.update { GameUiState(myPlayerIndex = 0, soundOn = it.soundOn, vsComputer = true) }
        updateSnapshot(MatchSnapshot(QuoridorRules.initialState(firstTurn = 0), revision = 0))
    }

    private fun applyLocal(pi: Int, action: QuoridorRules.Action) {
        val snap = _state.value.snapshot ?: return
        val next = QuoridorRules.apply(snap.state, pi, action)
        if (next == null) {
            _state.update { it.copy(error = "Invalid move") }
            return
        }
        updateSnapshot(MatchSnapshot(next, snap.revision + 1))
        if (next.status == "playing" && next.currentTurn != _state.value.myPlayerIndex) {
            val engine = ai ?: return
            viewModelScope.launch {
                _state.update { it.copy(submitting = true) }
                delay(500) // brief pause so the reply doesn't feel instant
                val reply = withContext(Dispatchers.Default) { engine.choose(next, next.currentTurn) }
                _state.update { it.copy(submitting = false) }
                applyLocal(next.currentTurn, reply)
            }
        }
    }

    fun init(roomId: String, myPlayerIndex: Int) {
        if (this.roomId == roomId) return
        this.roomId = roomId
        _state.update { it.copy(myPlayerIndex = myPlayerIndex) }
        repo.soundEnabled.onEach { on -> _state.update { it.copy(soundOn = on) } }.launchIn(viewModelScope)
        viewModelScope.launch { refresh(roomId) }
        repo.observeMatch(roomId)
            .onEach { updateSnapshot(it) }
            // Realtime dropped (network blip, app backgrounded): resync, then resubscribe.
            .retryWhen { _, _ -> delay(2000); refresh(roomId); true }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(nowMs = System.currentTimeMillis()) }
            }
        }
    }

    private suspend fun refresh(roomId: String) {
        runCatching { repo.getRoom(roomId) }
            .onSuccess { r -> r.match?.let { updateSnapshot(repo.snapshotOf(it)) } }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun toggleSound() {
        viewModelScope.launch { repo.setSoundEnabled(!_state.value.soundOn) }
    }

    private fun updateSnapshot(snap: MatchSnapshot) {
        var effect: Sfx? = null
        _state.update { s ->
            // Ignore stale/duplicate deliveries (refresh and realtime can race).
            if (s.snapshot != null && snap.revision <= s.snapshot.revision) return@update s
            // Only diff consecutive revisions; after a gap the history simply skips the missed moves.
            val record = s.snapshot
                ?.takeIf { snap.revision == it.revision + 1 }
                ?.let { MoveHistory.describe(it.state, snap.state) }
            effect = when {
                record == null -> null
                snap.state.status == "finished" ->
                    if (snap.state.winner == s.myPlayerIndex) Sfx.WIN else Sfx.LOSE
                record.wall != null -> Sfx.WALL
                else -> Sfx.MOVE
            }
            val history = if (record != null) s.history + record else s.history
            val moves = if (snap.state.currentTurn == s.myPlayerIndex)
                QuoridorRules.getValidMoves(snap.state, s.myPlayerIndex)
            else emptyList()
            val now = System.currentTimeMillis()
            s.copy(
                snapshot = snap, validMoves = moves, wallPreview = null, selectedCell = null, error = null,
                history = history,
                startedAtMs = s.startedAtMs ?: now,
                endedAtMs = s.endedAtMs ?: if (snap.state.status == "finished") now else null,
            )
        }
        effect?.let { if (_state.value.soundOn) sound.play(it) }
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
        if (!isValid) { flashInvalid(wr, wc, s.wallHorizontal); return }

        if (s.wallPreview?.row == wr && s.wallPreview.col == wc && s.wallPreview.horizontal == s.wallHorizontal) {
            // Second tap on same slot → confirm
            submitWall(roomId, wr, wc, s.wallHorizontal)
        } else {
            _state.update { it.copy(wallPreview = WallPreview(wr, wc, s.wallHorizontal), error = null) }
        }
    }

    /** A wall was dragged onto the board and released over notch ([wr], [wc]): place it, or flash if it's illegal. */
    fun onWallDrop(roomId: String, wr: Int, wc: Int) {
        val s = _state.value
        if (!s.isMyTurn || s.submitting || s.finished || s.mode != InteractionMode.WALL) return
        val gs = s.gameState ?: return
        if (QuoridorRules.isWallValid(gs, wr, wc, s.wallHorizontal, s.myPlayerIndex)) {
            submitWall(roomId, wr, wc, s.wallHorizontal)
        } else {
            flashInvalid(wr, wc, s.wallHorizontal)
        }
    }

    private fun flashInvalid(wr: Int, wc: Int, horizontal: Boolean) {
        _state.update {
            it.copy(
                error = "Invalid wall placement",
                invalidFlash = InvalidFlash(wr, wc, horizontal, (it.invalidFlash?.tick ?: 0) + 1),
            )
        }
        if (_state.value.soundOn) sound.play(Sfx.INVALID)
    }

    private fun submitMove(roomId: String, row: Int, col: Int) {
        if (isLocal) return applyLocal(_state.value.myPlayerIndex, QuoridorRules.Action.Move(row, col))
        submit(roomId) { rev -> repo.submitMove(roomId, rev, row, col) }
    }

    private fun submitWall(roomId: String, wr: Int, wc: Int, horizontal: Boolean) {
        _state.update { it.copy(wallPreview = null) }
        if (isLocal) return applyLocal(_state.value.myPlayerIndex, QuoridorRules.Action.Wall(wr, wc, horizontal))
        submit(roomId) { rev -> repo.submitWall(roomId, rev, wr, wc, horizontal) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun askResign(show: Boolean) = _state.update { it.copy(confirmResign = show) }

    fun resign(roomId: String) {
        _state.update { it.copy(confirmResign = false) }
        if (isLocal) {
            val snap = _state.value.snapshot ?: return
            val me = _state.value.myPlayerIndex
            updateSnapshot(MatchSnapshot(snap.state.copy(status = "finished", winner = 1 - me), snap.revision + 1))
            return
        }
        submit(roomId) { rev -> repo.resign(roomId, rev) }
    }

    fun claimTimeout(roomId: String) = submit(roomId) { repo.claimTimeout(roomId) }

    fun leave(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!isLocal) repo.clearRoom() // a local game must not wipe the online game we could resume
            onDone()
        }
    }

    private fun submit(roomId: String, call: suspend (revision: Int) -> Unit) = viewModelScope.launch {
        val rev = _state.value.snapshot?.revision ?: return@launch
        _state.update { it.copy(submitting = true) }
        try {
            call(rev)
        } catch (e: GameException) {
            if (e.isStale) refresh(roomId)
            _state.update { it.copy(error = if (e.isStale) "The board changed — try again." else e.message) }
        }
        _state.update { it.copy(submitting = false) }
    }
}
