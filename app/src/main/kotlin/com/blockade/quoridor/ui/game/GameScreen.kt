package com.blockade.quoridor.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockade.quoridor.domain.ai.AiDifficulty
import com.blockade.quoridor.ui.components.GameBackground
import com.blockade.quoridor.ui.game.components.ControlBar
import com.blockade.quoridor.ui.game.components.GameStats
import com.blockade.quoridor.ui.game.components.HintChip
import com.blockade.quoridor.ui.game.components.Identity
import com.blockade.quoridor.ui.game.components.MoveHistorySheet
import com.blockade.quoridor.ui.game.components.PlayerCard
import com.blockade.quoridor.ui.game.components.SecondaryActions
import com.blockade.quoridor.ui.game.components.TurnPill
import com.blockade.quoridor.ui.game.components.WinOverlay
import com.blockade.quoridor.ui.game.components.wallsLeftOf
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

    val gs = state.gameState
    val opponentName = if (state.vsComputer) "CPU" else "Opponent"

    GameBackground {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.gapSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- HUD ----------------------------------------------------------------------
            if (gs != null) {
                val me = state.myPlayerIndex
                val thinking = state.vsComputer && !state.isMyTurn && !state.finished
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                    PlayerCard(
                        name = "You", identity = Identity.YOU,
                        color = c.player, colorDark = c.playerDark,
                        wallsLeft = wallsLeftOf(gs, me),
                        active = state.isMyTurn && !state.finished, thinking = false,
                        modifier = Modifier.weight(1f),
                    )
                    PlayerCard(
                        name = opponentName,
                        identity = if (state.vsComputer) Identity.CPU else Identity.OPPONENT,
                        color = c.opponent, colorDark = c.opponentDark,
                        wallsLeft = wallsLeftOf(gs, 1 - me),
                        active = !state.isMyTurn && !state.finished, thinking = thinking,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Dimens.gap))
                TurnPill(
                    label = when {
                        state.finished -> "Game over"
                        state.isMyTurn -> "Your Turn"
                        state.vsComputer -> "CPU thinking"
                        else -> "Opponent's turn"
                    },
                    color = if (state.isMyTurn && !state.finished) c.player else c.opponent,
                    thinking = thinking,
                )
            }

            // ---- Board: the hero, centred in the space that's left ---------------------------
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.gap),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                if (gs != null) {
                    QuoridorBoard(
                        state = gs,
                        uiState = state,
                        onCellTap = { r, col -> vm.onCellTap(roomId, r, col) },
                        onWallSlotTap = { wr, wc -> vm.onWallSlotTap(roomId, wr, wc) },
                        modifier = Modifier.size(side),
                    )
                } else {
                    CircularProgressIndicator(color = c.player)
                }
            }

            // ---- Controls -----------------------------------------------------------------
            val canAct = state.isMyTurn && !state.finished && !state.submitting
            TimerChip(state, onClaim = { vm.claimTimeout(roomId) })
            AnimatedVisibility(visible = state.error != null, enter = fadeIn(), exit = fadeOut()) {
                HintChip(state.error ?: "", warning = true, modifier = Modifier.padding(bottom = Dimens.gapSmall))
            }
            HintChip(
                when {
                    state.finished -> "Game finished"
                    !state.isMyTurn -> "Waiting for $opponentName…"
                    state.mode == InteractionMode.MOVE -> "Tap a glowing dot to move"
                    else -> "Tap a groove to preview a wall, tap again to place it"
                },
                modifier = Modifier.padding(bottom = Dimens.gapSmall),
            )
            ControlBar(
                mode = state.mode,
                wallHorizontal = state.wallHorizontal,
                enabled = canAct,
                onMode = { if (it != state.mode) vm.toggleMode() },
                onRotate = vm::toggleWallOrientation,
            )
            Spacer(Modifier.height(Dimens.gapSmall))
            SecondaryActions(
                soundOn = state.soundOn,
                canResign = gs != null && !state.finished,
                historyCount = state.history.size,
                onResign = { vm.askResign(true) },
                onToggleSound = vm::toggleSound,
                onHistory = { showHistory = true },
            )
        }

        // ---- Overlays -------------------------------------------------------------------
        if (state.finished) {
            WinOverlay(
                won = state.winner == state.myPlayerIndex,
                winnerName = opponentName,
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.sheetRadius),
                title = { Text("Resign this game?") },
                text = { Text("$opponentName will be declared the winner.") },
                confirmButton = {
                    Button(
                        onClick = { vm.resign(roomId) },
                        colors = ButtonDefaults.buttonColors(containerColor = c.invalid, contentColor = c.onSurface),
                    ) { Text("Resign") }
                },
                dismissButton = { TextButton(onClick = { vm.askResign(false) }) { Text("Keep playing", color = c.onSurface) } },
            )
        }
        if (showHistory) {
            MoveHistorySheet(state.history, state.myPlayerIndex, opponentName, onDismiss = { showHistory = false })
        }
    }
}

/** Online only: the idle-turn countdown, or the claim-win button once the opponent has timed out. */
@Composable
private fun TimerChip(state: GameUiState, onClaim: () -> Unit) {
    val left = state.secondsLeft ?: return
    if (state.canClaimTimeout) {
        Button(
            onClick = onClaim,
            enabled = !state.submitting,
            modifier = Modifier.padding(bottom = Dimens.gapSmall),
        ) { Text("Opponent timed out — claim win") }
    } else {
        val who = if (state.isMyTurn) "Your" else "Opponent's"
        HintChip("$who time left: ${left / 60}:${"%02d".format(left % 60)}", modifier = Modifier.padding(bottom = Dimens.gapSmall))
    }
}
