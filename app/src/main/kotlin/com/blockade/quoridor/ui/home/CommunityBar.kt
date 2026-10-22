package com.blockade.quoridor.ui.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.data.model.CommunityStats
import com.blockade.quoridor.ui.theme.GameTheme

/** "● 12 online · 340 players joined". Hidden until the numbers have loaded. */
@Composable
fun CommunityBar(stats: CommunityStats?, modifier: Modifier = Modifier) {
    if (stats == null) return
    val c = GameTheme.colors
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "${stats.onlineCount} online, ${stats.totalUsers} players have joined"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(8.dp).background(c.active, CircleShape))
        Spacer(Modifier.width(8.dp))
        CountText(stats.onlineCount)
        Text(" online", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted)
        Text("  ·  ", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceMuted)
        CountText(stats.totalUsers)
        Text(
            if (stats.totalUsers == 1) " player joined" else " players joined",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceMuted,
        )
    }
}

/** A number that counts up to its value (and re-counts smoothly when it changes). */
@Composable
private fun CountText(value: Int) {
    val shown by animateIntAsState(value, tween(800), label = "count")
    Text("$shown", style = MaterialTheme.typography.bodyMedium, color = GameTheme.colors.onSurface, fontWeight = FontWeight.SemiBold)
}
