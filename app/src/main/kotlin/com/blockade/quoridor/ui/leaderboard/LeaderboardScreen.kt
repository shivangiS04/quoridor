package com.blockade.quoridor.ui.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockade.quoridor.data.model.LeaderboardEntry
import com.blockade.quoridor.ui.components.GameBackground
import com.blockade.quoridor.ui.profile.Avatar
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme

@Composable
fun LeaderboardScreen(onBack: () -> Unit, vm: LeaderboardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = GameTheme.colors
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }

    GameBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = Dimens.screenPadding, vertical = Dimens.gapSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(Dimens.touchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.onSurface)
                }
                Text("Leaderboard", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            }
            Text(
                "Top players by online wins",
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceMuted,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.onSurface)
                }
                state.error != null && state.entries.isEmpty() -> Message(state.error ?: "", c.invalid)
                state.entries.isEmpty() -> Message("No online wins yet. Win a game against a friend to be the first!", c.onSurfaceMuted)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    itemsIndexed(state.entries, key = { _, e -> e.id }) { i, e ->
                        LeaderboardRow(e, rank = i + 1, isMe = e.id == state.myId)
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, rank: Int, isMe: Boolean) {
    val c = GameTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Rank $rank, ${entry.displayName}${if (isMe) ", you" else ""}, ${entry.onlineWins} online wins"
            }
            .background(if (isMe) c.activeSurface else c.surface, shape)
            .border(BorderStroke(if (isMe) 1.5.dp else 1.dp, if (isMe) c.active else c.outline), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = if (rank <= 3) c.onSurface else c.onSurfaceMuted,
            fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.width(32.dp),
        )
        Avatar(entry.displayName, entry.photoUrl, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isMe) "${entry.displayName} (you)" else entry.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("${entry.gamesPlayed} games", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceMuted)
        }
        Text("${entry.onlineWins}", style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
        Text(" wins", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceMuted, modifier = Modifier.padding(start = 4.dp))
    }
    Spacer(Modifier.height(0.dp))
}
