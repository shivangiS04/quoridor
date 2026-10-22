package com.blockade.quoridor.ui.game.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme
import com.blockade.quoridor.ui.theme.Motion

enum class Identity(val icon: ImageVector) {
    YOU(Icons.Default.Person),
    CPU(Icons.Default.SmartToy),
    OPPONENT(Icons.Default.Diamond),
}

/**
 * One player's card: avatar chip, name and a stock of wall icons that deplete as walls are placed.
 * The active player's card glows; while [thinking] the whole card shimmers.
 */
@Composable
fun PlayerCard(
    name: String,
    identity: Identity,
    color: Color,
    colorDark: Color,
    wallsLeft: Int,
    active: Boolean,
    thinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = GameTheme.colors
    val infinite = rememberInfiniteTransition(label = "card")
    val glow = infinite.animateFloat(
        0.25f, 0.6f, infiniteRepeatable(tween(Motion.Pulse), RepeatMode.Reverse), label = "glow",
    )
    val shimmer = infinite.animateFloat(
        1f, 0.55f, infiniteRepeatable(tween(Motion.Pulse), RepeatMode.Reverse), label = "shimmer",
    )
    val border by animateColorAsState(if (active) color else c.outline, tween(Motion.Standard), label = "border")
    val shape = RoundedCornerShape(Dimens.cardRadius)

    Column(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = "$name, $wallsLeft walls left" }
            .drawBehind {
                if (active) {
                    drawRoundRect(
                        color.copy(alpha = glow.value * 0.5f),
                        topLeft = androidx.compose.ui.geometry.Offset(-4.dp.toPx(), -4.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                        cornerRadius = CornerRadius(Dimens.cardRadius.toPx() + 4.dp.toPx()),
                    )
                }
            }
            .background(c.surface, shape)
            .border(BorderStroke(if (active) 2.dp else 1.dp, border), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (thinking) shimmer.value else 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(Brush.linearGradient(listOf(color, colorDark)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(identity.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                Text(
                    "$wallsLeft walls",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        WallStock(wallsLeft, color)
    }
}

/** Ten small wall icons (two rows of five); spent ones shrink and fade. */
@Composable
private fun WallStock(wallsLeft: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in row * 5 until row * 5 + 5) {
                    val available = i < wallsLeft
                    val scale by animateFloatAsState(if (available) 1f else 0.55f, tween(Motion.Standard), label = "wall$i")
                    val tint by animateColorAsState(
                        if (available) color else GameTheme.colors.outline.copy(alpha = 0.5f),
                        tween(Motion.Standard), label = "wallColor$i",
                    )
                    Box(Modifier.width(14.dp).height(5.dp).scale(scale).background(tint, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

/** The centre badge: "Your Turn", "CPU thinking…" (animated dots), "Opponent's turn". The text crossfades. */
@Composable
fun TurnPill(
    label: String,
    color: Color,
    thinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = GameTheme.colors
    val bg by animateColorAsState(color.copy(alpha = 0.2f), tween(Motion.Standard), label = "pillBg")
    val edge by animateColorAsState(color, tween(Motion.Standard), label = "pillEdge")
    Row(
        modifier
            .background(bg, RoundedCornerShape(50))
            .border(1.5.dp, edge, RoundedCornerShape(50))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = label,
            transitionSpec = { fadeIn(tween(Motion.Standard)) togetherWith fadeOut(tween(Motion.Quick)) },
            label = "turnLabel",
        ) { text ->
            Text(text, style = MaterialTheme.typography.titleMedium, color = c.onSurface, fontWeight = FontWeight.SemiBold)
        }
        if (thinking) {
            Spacer(Modifier.width(6.dp))
            ThinkingDots(c.onSurface)
        }
    }
}

@Composable
private fun ThinkingDots(color: Color) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val a = t.animateFloat(
                0.25f, 0.25f,
                infiniteRepeatable(
                    keyframes {
                        durationMillis = 900
                        0.25f at (i * 150) using LinearEasing
                        1f at (i * 150 + 200) using LinearEasing
                        0.25f at (i * 150 + 400) using LinearEasing
                    },
                    RepeatMode.Restart,
                ),
                label = "dot$i",
            )
            Box(Modifier.size(5.dp).alpha(a.value).background(color, CircleShape))
        }
    }
}

/** Convenience: the walls remaining for a player index. */
fun wallsLeftOf(state: com.blockade.quoridor.domain.model.GameState, pi: Int): Int =
    state.players.getOrNull(pi)?.wallsLeft ?: QuoridorRules.WALLS_PER_PLAYER
