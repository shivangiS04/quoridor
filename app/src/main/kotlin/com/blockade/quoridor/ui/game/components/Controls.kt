package com.blockade.quoridor.ui.game.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.ui.game.InteractionMode
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GameTheme
import com.blockade.quoridor.ui.theme.Motion

/** A small wall piece, drawn horizontal; [rotation] turns it 90° so it can show the current wall orientation. */
@Composable
fun WallGlyph(tint: Color, modifier: Modifier = Modifier, rotation: Float = 0f) {
    Canvas(modifier.size(24.dp).graphicsLayer { rotationZ = rotation }) {
        val h = size.height * 0.26f
        drawRoundRect(
            tint,
            Offset(size.width * 0.05f, (size.height - h) / 2),
            Size(size.width * 0.9f, h),
            CornerRadius(h / 2),
        )
    }
}

/** "Move | Wall" segmented control with a sliding indicator. */
@Composable
fun ModeSwitch(
    mode: InteractionMode,
    enabled: Boolean,
    onSelect: (InteractionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = GameTheme.colors
    BoxWithConstraints(
        modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(c.surface, RoundedCornerShape(26.dp))
            .padding(4.dp),
    ) {
        val segment = maxWidth / 2
        val offset by animateDpAsState(
            if (mode == InteractionMode.MOVE) 0.dp else segment, tween(Motion.Standard, easing = Motion.Ease), label = "indicator",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .background(c.player, RoundedCornerShape(22.dp)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Segment("Move", InteractionMode.MOVE, mode, enabled, onSelect, Modifier.weight(1f)) { tint ->
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Segment("Wall", InteractionMode.WALL, mode, enabled, onSelect, Modifier.weight(1f)) { tint ->
                WallGlyph(tint, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun Segment(
    label: String,
    target: InteractionMode,
    current: InteractionMode,
    enabled: Boolean,
    onSelect: (InteractionMode) -> Unit,
    modifier: Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val c = GameTheme.colors
    val selected = target == current
    val tint by animateColorAsState(if (selected) c.groove else c.onSurfaceMuted, tween(Motion.Standard), label = "segTint")
    Row(
        modifier
            .fillMaxHeight()
            .selectable(selected = selected, enabled = enabled, role = Role.Tab) { onSelect(target) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
    }
}

/** Round button showing the wall orientation that will be placed; the glyph rotates 90° when tapped. */
@Composable
fun RotateWallButton(horizontal: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = GameTheme.colors
    val rotation by animateFloatAsState(if (horizontal) 0f else 90f, tween(Motion.Standard, easing = Motion.Ease), label = "rotate")
    Box(
        modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .background(c.surface, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (horizontal) "Wall is horizontal. Tap to make it vertical" else "Wall is vertical. Tap to make it horizontal" },
        contentAlignment = Alignment.Center,
    ) {
        WallGlyph(c.neutralWall, Modifier.size(28.dp), rotation)
    }
}

/** Mode switch plus the rotate button (only in wall mode). */
@Composable
fun ControlBar(
    mode: InteractionMode,
    wallHorizontal: Boolean,
    enabled: Boolean,
    onMode: (InteractionMode) -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ModeSwitch(mode, enabled, onMode, Modifier.weight(1f))
        AnimatedVisibility(
            visible = mode == InteractionMode.WALL,
            enter = fadeIn(tween(Motion.Standard)) + expandHorizontally(tween(Motion.Standard)),
            exit = fadeOut(tween(Motion.Quick)) + shrinkHorizontally(tween(Motion.Quick)),
        ) {
            Row {
                Spacer(Modifier.width(Dimens.gapSmall))
                RotateWallButton(wallHorizontal, enabled, onRotate)
            }
        }
    }
}

/** Small rounded chip with an icon and a line of guidance. */
@Composable
fun HintChip(text: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    val c = GameTheme.colors
    val tint = if (warning) c.invalid else c.onSurfaceMuted
    Row(
        modifier
            .background((if (warning) c.invalid else c.surface).copy(alpha = if (warning) 0.15f else 1f), RoundedCornerShape(Dimens.chipRadius))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (warning) Icons.Default.Info else Icons.Default.TouchApp, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** Resign / Sound / Move history, as labelled icon buttons. */
@Composable
fun SecondaryActions(
    soundOn: Boolean,
    canResign: Boolean,
    historyCount: Int,
    onResign: () -> Unit,
    onToggleSound: () -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ActionButton(Icons.Default.Flag, "Resign", enabled = canResign, onClick = onResign)
        ActionButton(
            if (soundOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            if (soundOn) "Sound on" else "Sound off", enabled = true, onClick = onToggleSound,
        )
        ActionButton(Icons.Default.History, "Moves ($historyCount)", enabled = true, onClick = onHistory)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = GameTheme.colors
    val tint = if (enabled) c.onSurface else c.onSurfaceMuted.copy(alpha = 0.4f)
    // The whole icon + label is the touch target (min 48dp), not just the icon.
    Column(
        Modifier
            .heightIn(min = Dimens.touchTarget)
            .widthIn(min = Dimens.touchTarget * 1.4f)
            .clip(RoundedCornerShape(Dimens.chipRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceMuted)
    }
}
