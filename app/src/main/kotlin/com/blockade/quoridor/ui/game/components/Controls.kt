package com.blockade.quoridor.ui.game.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.ui.game.WallDragState
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme

/** Back, Resign, and the sound toggle. */
@Composable
fun GameTopBar(
    canResign: Boolean,
    soundOn: Boolean,
    onBack: () -> Unit,
    onResign: () -> Unit,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = GameTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(Dimens.touchTarget)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        val shape = RoundedCornerShape(Dimens.chipRadius)
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .alpha(if (canResign) 1f else 0.4f)
                .background(c.player.copy(alpha = 0.12f), shape)
                .border(BorderStroke(1.dp, c.player.copy(alpha = 0.55f)), shape)
                .clickable(enabled = canResign, role = Role.Button, onClick = onResign)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Flag, contentDescription = null, tint = c.player, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Resign", style = MaterialTheme.typography.titleMedium, color = c.player)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onToggleSound, modifier = Modifier.size(Dimens.touchTarget)) {
            Icon(
                if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (soundOn) "Mute" else "Unmute",
                tint = c.onSurfaceMuted,
            )
        }
    }
}

/** One quiet line under the cards: the latest move (or "No moves yet"). Tap for the full history. */
@Composable
fun HistoryStrip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = GameTheme.colors
    val shape = RoundedCornerShape(Dimens.chipRadius)
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = c.onSurfaceMuted,
        maxLines = 1,
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, c.outline), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = "$text. Open move history" },
    )
}

/** A short message line (illegal wall, turn timer). */
@Composable
fun HintChip(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val c = GameTheme.colors
    val tint = if (warning) c.invalid else c.onSurfaceMuted
    Row(
        modifier
            .background(if (warning) c.invalid.copy(alpha = 0.14f) else c.surface, RoundedCornerShape(Dimens.chipRadius))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Info, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/**
 * The two wall pieces, Horizontal and Vertical. Tap one to choose the orientation used when you tap a groove
 * on the board; press and drag one onto the board to place it there.
 */
@Composable
fun WallTray(
    horizontalSelected: Boolean,
    enabled: Boolean,
    color: Color,
    drag: WallDragState,
    onSelect: (horizontal: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.gap, Alignment.CenterHorizontally)) {
        WallTrayButton("Horizontal", true, horizontalSelected, enabled, color, drag, onSelect)
        WallTrayButton("Vertical", false, !horizontalSelected, enabled, color, drag, onSelect)
    }
}

@Composable
private fun WallTrayButton(
    label: String,
    horizontal: Boolean,
    selected: Boolean,
    enabled: Boolean,
    color: Color,
    drag: WallDragState,
    onSelect: (Boolean) -> Unit,
) {
    val c = GameTheme.colors
    val shape = RoundedCornerShape(Dimens.cardRadius)
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    Row(
        Modifier
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 52.dp)
            .background(c.surface, shape)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) c.onSurface else c.outline), shape)
            .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(horizontal) }
            .pointerInput(enabled, horizontal) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { local ->
                        onSelect(horizontal)
                        drag.start(horizontal, rootOffset + local)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        drag.move(rootOffset + change.position)
                    },
                    onDragEnd = { drag.end() },
                    onDragCancel = { drag.cancel() },
                )
            }
            .padding(horizontal = 18.dp)
            .semantics { contentDescription = "$label wall. Drag onto the board to place it" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(28.dp)) {
            val thick = size.minDimension * 0.22f
            val length = size.minDimension * 0.92f
            val sz = if (horizontal) Size(length, thick) else Size(thick, length)
            drawRoundRect(
                color,
                Offset((size.width - sz.width) / 2, (size.height - sz.height) / 2),
                sz,
                CornerRadius(thick / 2),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = c.onSurface)
    }
}
