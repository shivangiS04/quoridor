package com.blockade.quoridor.ui.game.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme
import com.blockade.quoridor.ui.theme.Motion

/**
 * A flat player card: name, the player's colour as a small bar, and walls left out of ten.
 * The player whose turn it is gets a green border.
 */
@Composable
fun PlayerCard(
    name: String,
    color: Color,
    wallsLeft: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = GameTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    val border by animateColorAsState(if (active) c.active else c.outline, tween(Motion.Standard), label = "cardBorder")
    val fill by animateColorAsState(if (active) c.activeSurface else c.surface, tween(Motion.Standard), label = "cardFill")

    Column(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = "$name, $wallsLeft of ${QuoridorRules.WALLS_PER_PLAYER} walls left" }
            .background(fill, shape)
            .border(BorderStroke(if (active) 1.5.dp else 1.dp, border), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(width = 20.dp, height = 6.dp).background(color, RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(10.dp))
            Text("$wallsLeft", style = MaterialTheme.typography.titleMedium, color = c.onSurface, fontWeight = FontWeight.SemiBold)
            Text(" /${QuoridorRules.WALLS_PER_PLAYER}", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted)
        }
    }
}
