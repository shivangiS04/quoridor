package com.blockade.quoridor.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun GameScreen(
    roomId: String,
    myPlayerIndex: Int,
    onLeave: () -> Unit,
    vm: GameViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(roomId) { vm.init(roomId, myPlayerIndex) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status row
            StatusBar(state, myPlayerIndex)
            Spacer(Modifier.height(8.dp))

            // Board
            val gs = state.gameState
            if (gs != null) {
                QuoridorBoard(
                    state = gs,
                    uiState = state,
                    onCellTap = { r, c -> vm.onCellTap(roomId, r, c) },
                    onWallSlotTap = { wr, wc -> vm.onWallSlotTap(roomId, wr, wc) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            Spacer(Modifier.height(12.dp))

            // Turn timer / forfeit
            TimerRow(state, onClaim = { vm.claimTimeout(roomId) })

            // Controls
            ControlRow(state, vm)

            if (!state.finished && state.gameState != null) {
                TextButton(onClick = { vm.askResign(true) }) { Text("Resign") }
            }

            // Error
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Win dialog
            if (state.finished) {
                WinDialog(winner = state.winner, myIndex = myPlayerIndex, onLeave = { vm.leave(onLeave) })
            }

            if (state.confirmResign) {
                AlertDialog(
                    onDismissRequest = { vm.askResign(false) },
                    title = { Text("Resign?") },
                    text = { Text("Your opponent will win the game.") },
                    confirmButton = { TextButton(onClick = { vm.resign(roomId) }) { Text("Resign") } },
                    dismissButton = { TextButton(onClick = { vm.askResign(false) }) { Text("Keep playing") } },
                )
            }
        }
    }
}

@Composable
private fun StatusBar(state: GameUiState, myPlayerIndex: Int) {
    val gs = state.gameState ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // My walls remaining
        Text("Your walls: ${gs.players[myPlayerIndex].wallsLeft}", style = MaterialTheme.typography.labelLarge)

        // Turn indicator
        val turnText = when {
            state.finished -> if (state.winner == myPlayerIndex) "You win!" else "You lose"
            state.isMyTurn -> "Your turn"
            else -> "Opponent's turn"
        }
        Text(turnText, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        // Opponent walls
        Text("Opp walls: ${gs.players[1 - myPlayerIndex].wallsLeft}", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TimerRow(state: GameUiState, onClaim: () -> Unit) {
    val left = state.secondsLeft ?: return
    if (state.canClaimTimeout) {
        Button(onClick = onClaim, enabled = !state.submitting) { Text("Opponent timed out — claim win") }
    } else {
        val who = if (state.isMyTurn) "Your" else "Opponent's"
        Text(
            "$who time left: ${left / 60}:${"%02d".format(left % 60)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlRow(state: GameUiState, vm: GameViewModel) {
    if (!state.isMyTurn || state.finished) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mode toggle
        FilterChip(
            selected = state.mode == InteractionMode.MOVE,
            onClick = { if (state.mode != InteractionMode.MOVE) vm.toggleMode() },
            label = { Text("Move") },
            leadingIcon = { Icon(Icons.Outlined.DirectionsWalk, null, Modifier.size(18.dp)) },
        )
        Spacer(Modifier.width(12.dp))
        FilterChip(
            selected = state.mode == InteractionMode.WALL,
            onClick = { if (state.mode != InteractionMode.WALL) vm.toggleMode() },
            label = { Text("Wall") },
            leadingIcon = { Icon(Icons.Default.GridOn, null, Modifier.size(18.dp)) },
        )

        // Orientation toggle (only in wall mode)
        if (state.mode == InteractionMode.WALL) {
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = vm::toggleWallOrientation) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription = if (state.wallHorizontal) "Switch to vertical" else "Switch to horizontal",
                )
            }
            Text(if (state.wallHorizontal) "H" else "V", style = MaterialTheme.typography.labelSmall)
        }

        if (state.submitting) {
            Spacer(Modifier.width(16.dp))
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
    if (state.mode == InteractionMode.WALL) {
        Text(
            "Tap a wall slot to preview, tap again to place",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            "Tap a highlighted cell to move",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WinDialog(winner: Int?, myIndex: Int, onLeave: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (winner == myIndex) "You win!" else "You lose") },
        text = { Text(if (winner == myIndex) "You reached the other side!" else "Your opponent got there first.") },
        confirmButton = { TextButton(onClick = onLeave) { Text("Back to lobby") } },
    )
}
