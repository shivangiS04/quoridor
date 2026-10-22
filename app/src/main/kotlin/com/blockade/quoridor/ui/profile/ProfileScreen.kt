package com.blockade.quoridor.ui.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.blockade.quoridor.data.model.Profile
import com.blockade.quoridor.ui.components.GameBackground
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme

@Composable
fun ProfileScreen(onBack: () -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = GameTheme.colors
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }

    GameBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding, vertical = Dimens.gapSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(Dimens.touchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.onSurface)
                }
                Text("Profile", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            }
            val p = state.profile
            if (p == null) {
                Spacer(Modifier.height(48.dp))
                if (state.loading) CircularProgressIndicator(color = c.onSurface)
                state.error?.let { Text(it, color = c.invalid, textAlign = TextAlign.Center) }
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            Avatar(p, Modifier.size(96.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                if (p.isGuest) "Guest account" else "Google account",
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceMuted,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = state.nameDraft,
                onValueChange = vm::setName,
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::save,
                enabled = state.nameChanged && !state.saving,
                modifier = Modifier.fillMaxWidth().height(Dimens.touchTarget),
                shape = RoundedCornerShape(Dimens.cardRadius),
                colors = ButtonDefaults.buttonColors(containerColor = c.onSurface, contentColor = c.background),
            ) { Text("Save name") }
            state.message?.let { Text(it, color = c.active, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
            state.error?.let { Text(it, color = c.invalid, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }

            Spacer(Modifier.height(24.dp))
            StatsGrid(p)

            Spacer(Modifier.height(24.dp))
            if (p.isGuest) {
                OutlinedButton(
                    onClick = vm::linkGoogle,
                    modifier = Modifier.fillMaxWidth().height(Dimens.touchTarget),
                    shape = RoundedCornerShape(Dimens.cardRadius),
                    border = BorderStroke(1.dp, c.outline),
                ) { Text("Link Google account", color = c.onSurface) }
                Text(
                    "Keeps your stats if you change phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedButton(
                onClick = vm::signOut,
                modifier = Modifier.fillMaxWidth().height(Dimens.touchTarget),
                shape = RoundedCornerShape(Dimens.cardRadius),
                border = BorderStroke(1.dp, c.player.copy(alpha = 0.5f)),
            ) { Text("Sign out", color = c.player) }
            if (p.isGuest) {
                Text(
                    "Signing out of a guest account loses its stats for good.",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceMuted,
                    modifier = Modifier.padding(top = 6.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The profile photo, or the first letter of the name on a flat disc when there is none. */
@Composable
fun Avatar(profile: Profile, modifier: Modifier = Modifier) = Avatar(profile.displayName, profile.photoUrl, modifier)

@Composable
fun Avatar(name: String, photoUrl: String?, modifier: Modifier = Modifier) {
    val c = GameTheme.colors
    Box(
        modifier.clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "$name's photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = c.onSurface)
        }
    }
}

@Composable
private fun StatsGrid(p: Profile) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            Stat("Games", p.gamesPlayed, Modifier.weight(1f))
            Stat("Wins", p.wins, Modifier.weight(1f))
            Stat("Losses", p.losses, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            Stat("Wins vs computer", p.aiWins, Modifier.weight(1f))
            Stat("Online wins", p.onlineWins, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    val c = GameTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    Column(
        modifier.background(c.surface, shape).border(1.dp, c.outline, shape).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = c.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceMuted, textAlign = TextAlign.Center)
    }
}
