package com.blockade.quoridor.ui.game.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme
import com.blockade.quoridor.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

data class GameStats(val moves: Int, val wallsUsed: Int, val seconds: Long)

/**
 * Full-screen result. A win gets confetti and a celebratory title; a loss is softer and encouraging.
 * "Play Again" is only offered when [onPlayAgain] is provided (single-player).
 */
@Composable
fun WinOverlay(
    won: Boolean,
    winnerName: String,
    stats: GameStats,
    onPlayAgain: (() -> Unit)?,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = GameTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        if (shown) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "overlayIn",
    )
    val scrim by animateFloatAsState(if (shown) 0.72f else 0f, tween(Motion.Standard), label = "scrim")

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim))
            // Swallow taps so the board underneath can't be touched.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        if (won) Confetti()
        val accent = if (won) c.player else c.opponent
        Column(
            Modifier
                .padding(Dimens.screenPadding * 1.5f)
                .scale(appear.coerceAtLeast(0.01f))
                .graphicsLayer { alpha = appear.coerceIn(0f, 1f) }
                .background(c.surface, RoundedCornerShape(28.dp))
                .border(2.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (won) "You Win!" else "$winnerName Wins",
                style = MaterialTheme.typography.displayMedium,
                color = accent,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (won) "You reached the other side." else "So close — every game makes you sharper.",
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
                    colors = ButtonDefaults.buttonColors(containerColor = c.player, contentColor = c.groove),
                ) { Text("Play Again", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth().height(Dimens.touchTarget),
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

private class Piece(
    val x: Float, val speed: Float, val phase: Float, val sway: Float, val size: Float, val spin: Float, val color: Color,
)

/** Looping confetti; one animated clock drives every piece, and it is only read while drawing. */
@Composable
private fun Confetti() {
    val c = GameTheme.colors
    val palette = listOf(c.player, c.opponent, c.neutralWall, c.success, Color.White)
    val pieces = remember {
        val rng = Random(42)
        List(70) {
            Piece(rng.nextFloat(), 0.6f + rng.nextFloat() * 0.9f, rng.nextFloat(), 10f + rng.nextFloat() * 26f,
                6f + rng.nextFloat() * 8f, rng.nextFloat() * 360f, palette[rng.nextInt(palette.size)])
        }
    }
    val clock = rememberInfiniteTransition(label = "confetti").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        for (p in pieces) {
            val y = (((t * p.speed + p.phase) % 1f) * (size.height + 60f)) - 30f
            val x = p.x * size.width + sin((t * 2 * PI + p.phase * 6).toFloat()) * p.sway
            rotate(p.spin + t * 360f * p.speed, pivot = Offset(x, y)) {
                drawRect(p.color, Offset(x - p.size / 2, y - p.size / 3), Size(p.size, p.size * 0.6f))
            }
        }
    }
}
