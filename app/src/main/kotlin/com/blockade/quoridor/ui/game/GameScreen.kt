package com.blockade.quoridor.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockade.quoridor.domain.ai.AiDifficulty
import com.blockade.quoridor.ui.components.GameBackground
import com.blockade.quoridor.ui.game.components.GameStats
import com.blockade.quoridor.ui.game.components.GameTopBar
import com.blockade.quoridor.ui.game.components.HintChip
import com.blockade.quoridor.ui.game.components.HistoryStrip
import com.blockade.quoridor.ui.game.components.MoveHistorySheet
import com.blockade.quoridor.ui.game.components.PlayerCard
import com.blockade.quoridor.ui.game.components.WallTray
import com.blockade.quoridor.ui.game.components.WinOverlay
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme

@Composable
fun GameScreen(
    roomId: String,
    myPlayerIndex: Int,
    onLeave: () -> Unit,
    aiDifficulty: AiDifficulty? = null,
    vm: GameViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = GameTheme.colors
    val haptic = LocalHapticFeedback.current
    var showHistory by remember { mutableStateOf(false) }
    val drag = remember { WallDragState() }

    LaunchedEffect(roomId, aiDifficulty) {
        if (aiDifficulty != null) vm.initLocal(aiDifficulty) else vm.init(roomId, myPlayerIndex)
    }

    // Haptics: a light tick for a pawn move, a firmer one for a wall, and one for an illegal wall.
    LaunchedEffect(state.history.size) {
        state.history.lastOrNull()?.let {
            haptic.performHapticFeedback(if (it.wall != null) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(state.invalidFlash?.tick) {
        if (state.invalidFlash != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.error) {
        if (state.error != null) {
            kotlinx.coroutines.delay(2500)
            vm.clearError()
        }
    }
    // A wall in the hand is dropped if the game ends or the turn passes while dragging.
    LaunchedEffect(state.isMyTurn, state.finished) {
        if (!state.isMyTurn || state.finished) drag.cancel()
    }

    val gs = state.gameState
    val opponentName = state.opponentLabel ?: "Opponent"      // e.g. "Computer · Easy" (card title)
    val opponentShort = opponentName.substringBefore(" · ")  // e.g. "Computer" (everywhere else)
    val me = state.myPlayerIndex
    val canPlaceWall = state.isMyTurn && !state.finished && !state.submitting &&
        gs != null && gs.players[me].wallsLeft > 0

    GameBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.gapSmall),
        ) {
            GameTopBar(
                canResign = gs != null && !state.finished,
                soundOn = state.soundOn,
                onBack = onLeave,
                onResign = { vm.askResign(true) },
                onToggleSound = vm::toggleSound,
            )
            Spacer(Modifier.height(Dimens.gapSmall))

            if (gs != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                    PlayerCard(
                        name = "You", color = c.player, wallsLeft = gs.players[me].wallsLeft,
                        active = state.isMyTurn && !state.finished, modifier = Modifier.weight(1f),
                    )
                    PlayerCard(
                        name = opponentName, color = c.opponent, wallsLeft = gs.players[1 - me].wallsLeft,
                        active = !state.isMyTurn && !state.finished, modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Dimens.gapSmall))

            val thinking = state.vsComputer && !state.isMyTurn && !state.finished
            HistoryStrip(
                text = when {
                    thinking -> "$opponentShort is thinking…"
                    state.history.isEmpty() -> "No moves yet"
                    else -> state.history.last().let { m ->
                        if (m.gameEnd) "Game over"
                        else "${state.history.size}. ${if (m.player == me) "You" else opponentShort}: ${m.text}"
                    }
                },
                onClick = { showHistory = true },
            )

            // The board is the hero: centred in whatever space is left.
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.gap),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                if (gs != null) {
                    QuoridorBoard(
                        state = gs,
                        uiState = state,
                        drag = drag,
                        onCellTap = { r, col -> vm.onCellTap(roomId, r, col) },
                        onWallSlotTap = { wr, wc -> vm.onWallSlotTap(roomId, wr, wc) },
                        onWallDrop = { wr, wc -> vm.onWallDrop(roomId, wr, wc) },
                        modifier = Modifier.size(side),
                    )
                } else {
                    CircularProgressIndicator(color = c.onSurface)
                }
            }

            // One fixed-height slot for transient messages, so the board never shifts when they appear.
            Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.error != null) {
                    HintChip(state.error ?: "", warning = true)
                } else if (aiDifficulty == null) {
                    TimerChip(state, onClaim = { vm.claimTimeout(roomId) })
                }
            }
            WallTray(
                horizontalSelected = state.wallHorizontal,
                enabled = canPlaceWall,
                color = c.player,
                drag = drag,
                onSelect = vm::setWallOrientation,
            )
            Spacer(Modifier.height(Dimens.gapSmall))
        }

        // The wall in the hand, drawn above everything so it can be carried anywhere on screen.
        if (drag.active && drag.boardSidePx > 0f) {
            val lift = with(LocalDensity.current) { WALL_DRAG_LIFT.toPx() }
            Canvas(Modifier.fillMaxSize()) {
                val bar = wallBarSize(drag.boardSidePx, drag.horizontal)
                val center = drag.finger - Offset(0f, lift)
                val color = when (drag.targetValid) {
                    true -> c.player
                    false -> c.invalid
                    null -> c.player.copy(alpha = 0.6f)
                }
                val tl = center - Offset(bar.width / 2, bar.height / 2)
                val radius = CornerRadius(minOf(bar.width, bar.height) / 2)
                drawRoundRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f), tl + Offset(0f, 4.dp.toPx()), bar, radius)
                drawRoundRect(color, tl, bar, radius)
            }
        }

        if (state.finished) {
            WinOverlay(
                won = state.winner == me,
                opponentName = opponentShort,
                stats = GameStats(state.myTurnsTaken, state.wallsUsed, state.elapsedSeconds),
                onPlayAgain = if (state.vsComputer) vm::restartLocal else null,
                onHome = { vm.leave(onLeave) },
            )
        }
        if (state.confirmResign) {
            AlertDialog(
                onDismissRequest = { vm.askResign(false) },
                containerColor = c.surface,
                titleContentColor = c.onSurface,
                textContentColor = c.onSurfaceMuted,
                shape = RoundedCornerShape(Dimens.sheetRadius),
                title = { Text("Resign this game?") },
                text = { Text("$opponentShort will be declared the winner.") },
                confirmButton = {
                    Button(
                        onClick = { vm.resign(roomId) },
                        colors = ButtonDefaults.buttonColors(containerColor = c.player, contentColor = c.onSurface),
                    ) { Text("Resign") }
                },
                dismissButton = { TextButton(onClick = { vm.askResign(false) }) { Text("Keep playing", color = c.onSurface) } },
            )
        }
        if (showHistory) {
            MoveHistorySheet(state.history, me, opponentShort, onDismiss = { showHistory = false })
        }
    }
}

/** Online only: the idle-turn countdown, or the claim-win button once the opponent has timed out. */
@Composable
private fun TimerChip(state: GameUiState, onClaim: () -> Unit) {
    val left = state.secondsLeft ?: return
    if (state.canClaimTimeout) {
        Button(onClick = onClaim, enabled = !state.submitting) { Text("Opponent timed out — claim win") }
    } else {
        val who = if (state.isMyTurn) "Your" else "Opponent's"
        HintChip("$who time left: ${left / 60}:${"%02d".format(left % 60)}")
    }
}
