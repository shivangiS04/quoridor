package com.blockade.quoridor.ui.game.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme
import com.blockade.quoridor.ui.theme.Motion

data class GameStats(val moves: Int, val wallsUsed: Int, val seconds: Long)

/** The result screen: a plain card over a dimmed board, with the game's stats. "Play Again" only in single-player. */
@Composable
fun WinOverlay(
    won: Boolean,
    opponentName: String,
    stats: GameStats,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = GameTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(Motion.Standard), label = "overlayIn")
    val accent = if (won) c.active else c.player
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f * appear))
            // Swallow taps so the board underneath can't be touched.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(Dimens.screenPadding * 1.5f)
                .scale(0.96f + 0.04f * appear)
                .graphicsLayer { alpha = appear }
                .background(c.surface, shape)
                .border(BorderStroke(1.dp, c.outline), shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (won) "You won" else "You lost",
                style = MaterialTheme.typography.headlineLarge,
                color = accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (won) "You reached the other side." else "$opponentName reached the other side.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Moves", stats.moves.toString())
                Stat("Walls used", stats.wallsUsed.toString())
                Stat("Time", "%d:%02d".format(stats.seconds / 60, stats.seconds % 60))
            }
            Spacer(Modifier.height(24.dp))
            if (onPlayAgain != null) {
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.fillMaxWidth().height(Dimens.touchTarget),
                    shape = RoundedCornerShape(Dimens.cardRadius),
                    colors = ButtonDefaults.buttonColors(containerColor = c.onSurface, contentColor = c.background),
                ) { Text("Play again", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().height(Dimens.touchTarget),
                shape = RoundedCornerShape(Dimens.cardRadius),
                border = BorderStroke(1.dp, c.outline),
            ) { Text("Home", style = MaterialTheme.typography.titleMedium, color = c.onSurface) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val c = GameTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceMuted)
    }
}
