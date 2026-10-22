package com.blockade.quoridor.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.WallPos
import com.blockade.quoridor.domain.rules.PlacedWall
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.ui.theme.Dimens
import com.blockade.quoridor.ui.theme.GamePalette
import com.blockade.quoridor.ui.theme.GameTheme
import com.blockade.quoridor.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val CELLS = QuoridorRules.BOARD            // 9
private const val WALL_GRID = QuoridorRules.WALL_GRID    // 8
private const val GAP_FRACTION = 0.18f                   // gutter = 18% of a cell, wide enough to hold a wall piece

private fun Color.lighten(f: Float) = lerpColor(this, Color.White, f)
private fun Color.darken(f: Float) = lerpColor(this, Color.Black, f)

/** Cell/gutter size in px for a square canvas of [total] px. */
private class Metrics(total: Float) {
    val gap = total / (CELLS + (CELLS - 1) * GAP_FRACTION) * GAP_FRACTION
    val cell = (total - gap * (CELLS - 1)) / CELLS
    val stride = cell + gap
}

/**
 * The board. Drawn as three stacked layers so that the constant "breathing" animation only redraws the
 * pawns and hints, never the 81 tiles or the walls:
 *   1. tiles, grooves and goal rows (static)
 *   2. walls, wall preview, invalid-placement ghost
 *   3. valid-move hints and pawns (+ the tap handling)
 */
@Composable
fun QuoridorBoard(
    state: GameState,
    uiState: GameUiState,
    onCellTap: (row: Int, col: Int) -> Unit,
    onWallSlotTap: (wr: Int, wc: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GameTheme.colors
    val me = uiState.myPlayerIndex
    val onCell by rememberUpdatedState(onCellTap)
    val onWall by rememberUpdatedState(onWallSlotTap)

    // --- animation state -------------------------------------------------------------------
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.Pulse, easing = Motion.Ease), RepeatMode.Reverse),
        label = "pulseValue",
    )

    // Board shake: a tiny one when a wall lands, a bigger one (with a red flash) for an illegal wall.
    val shake = remember { Animatable(1f) }
    var shakeAmplitude by remember { mutableFloatStateOf(0f) }
    val lastWall = uiState.lastWall
    val revision = uiState.snapshot?.revision
    val wallIn = remember { Animatable(1f) }
    LaunchedEffect(lastWall, revision) {
        if (lastWall != null) {
            shakeAmplitude = 3f
            shake.snapTo(0f)
            wallIn.snapTo(0f)
            // The wall settles with a slight overshoot while the board gives a little thud.
            wallIn.animateTo(1f, tween(Motion.Wall, easing = Motion.Settle))
            shake.animateTo(1f, tween(Motion.Shake))
        }
    }
    val invalid = uiState.invalidFlash
    val invalidAlpha = remember { Animatable(0f) }
    LaunchedEffect(invalid?.tick) {
        if (invalid != null) {
            shakeAmplitude = 8f
            shake.snapTo(0f)
            invalidAlpha.snapTo(1f)
            shake.animateTo(1f, tween(Motion.Shake))
            invalidAlpha.animateTo(0f, tween(Motion.Shake))
        }
    }

    val myPawn = rememberPawnMotion(state.players[me].row, state.players[me].col)
    val oppPawn = rememberPawnMotion(state.players[1 - me].row, state.players[1 - me].col)
    val owners = remember(uiState.history) { uiState.wallOwners }

    Box(
        modifier = modifier
            .graphicsLayer {
                val density = this.density
                translationX = sin(shake.value * PI.toFloat() * 6f) * (1f - shake.value) * shakeAmplitude * density
            }
            .drawBehind { drawBoardFrame(colors) }
            .padding(12.dp),
    ) {
        // 1 — tiles, grooves, goal rows
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val m = Metrics(size.width)
            drawTiles(m, colors, goalRowMe = QuoridorRules.goalRow(me), goalRowOpp = QuoridorRules.goalRow(1 - me))
        }

        // 2 — walls
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val m = Metrics(size.width)
            fun colorFor(w: WallPos, horizontal: Boolean): Color =
                when (owners[PlacedWall(w, horizontal)]) {
                    me -> colors.player
                    null -> colors.neutralWall
                    else -> colors.opponent
                }
            for (w in state.walls.horizontal) {
                val isNew = lastWall != null && lastWall.horizontal && lastWall.pos == w
                drawWallPiece(w, true, m, colorFor(w, true), if (isNew) wallIn.value else 1f)
            }
            for (w in state.walls.vertical) {
                val isNew = lastWall != null && !lastWall.horizontal && lastWall.pos == w
                drawWallPiece(w, false, m, colorFor(w, false), if (isNew) wallIn.value else 1f)
            }
            uiState.wallPreview?.let { p ->
                drawWallGhost(WallPos(p.row, p.col), p.horizontal, m, colors.player, fillAlpha = 0.4f, outlineAlpha = 0.95f)
            }
            if (invalid != null && invalidAlpha.value > 0.01f) {
                drawWallGhost(
                    WallPos(invalid.row, invalid.col), invalid.horizontal, m, colors.invalid,
                    fillAlpha = 0.55f * invalidAlpha.value, outlineAlpha = invalidAlpha.value,
                )
            }
        }

        // 3 — hints, pawns, taps
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val m = Metrics(size.width.toFloat())
                        val (tapRow, tapType) = coordToSlot(offset.y, m.cell, m.gap, CELLS)
                        val (tapCol, tapTypeC) = coordToSlot(offset.x, m.cell, m.gap, CELLS)
                        if (tapType == SlotType.CELL && tapTypeC == SlotType.CELL) {
                            onCell(tapRow, tapCol)
                        } else if (tapType == SlotType.GAP || tapTypeC == SlotType.GAP) {
                            // Wall slot: use the notch grid index
                            val wr = if (tapType == SlotType.GAP) tapRow else if (tapRow == CELLS - 1) tapRow - 1 else tapRow
                            val wc = if (tapTypeC == SlotType.GAP) tapCol else if (tapCol == CELLS - 1) tapCol - 1 else tapCol
                            if (wr in 0 until WALL_GRID && wc in 0 until WALL_GRID) onWall(wr, wc)
                        }
                    }
                },
        ) {
            val m = Metrics(size.width)

            // Valid destinations: a glowing dot with a soft breathing ring.
            if (uiState.mode == InteractionMode.MOVE) {
                for (p in uiState.validMoves) {
                    val center = Offset(p.col * m.stride + m.cell / 2, p.row * m.stride + m.cell / 2)
                    val ring = m.cell * (0.24f + 0.10f * pulse.value)
                    drawCircle(colors.player.copy(alpha = 0.28f - 0.12f * pulse.value), ring, center)
                    drawCircle(colors.player.copy(alpha = 0.95f), m.cell * 0.10f, center)
                }
            }

            val turn = state.currentTurn.takeIf { state.status == "playing" }
            // Draw the pawn that is lower on screen last so a hop never goes behind the other one.
            val order = if (myPawn.position.y <= oppPawn.position.y) listOf(0, 1) else listOf(1, 0)
            for (who in order) {
                val mine = who == 0
                val pawn = if (mine) myPawn else oppPawn
                val pi = if (mine) me else 1 - me
                drawPawn(
                    m = m,
                    pos = pawn.position,
                    hop = pawn.hop,
                    base = if (mine) colors.player else colors.opponent,
                    dark = if (mine) colors.playerDark else colors.opponentDark,
                    glyph = when {
                        mine -> Glyph.PERSON
                        uiState.vsComputer -> Glyph.ROBOT
                        else -> Glyph.DIAMOND
                    },
                    breathe = if (turn == pi) pulse.value else 0f,
                    active = turn == pi,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pawn motion: slide + hop (and a higher arc for jumps)
// ---------------------------------------------------------------------------------------------

private class PawnMotion(start: Offset) {
    val progress = Animatable(1f)
    var from by mutableStateOf(start)
    var to by mutableStateOf(start)

    /** Current position in cell units (x = column, y = row). */
    val position: Offset get() = lerpOffset(from, to, progress.value)

    /** Height of the hop in cell units; a jump over the opponent (distance > 1) arcs higher. */
    val hop: Float get() {
        val dist = (to - from).getDistance()
        val peak = if (dist > 1.2f) 0.45f else 0.18f
        return sin(PI.toFloat() * progress.value) * peak
    }

    suspend fun moveTo(target: Offset) {
        if (target == to) return
        from = position
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(Motion.Pawn, easing = Motion.Ease))
    }
}

@Composable
private fun rememberPawnMotion(row: Int, col: Int): PawnMotion {
    val motion = remember { PawnMotion(Offset(col.toFloat(), row.toFloat())) }
    LaunchedEffect(row, col) { motion.moveTo(Offset(col.toFloat(), row.toFloat())) }
    return motion
}

// ---------------------------------------------------------------------------------------------
// Hit testing (unchanged behaviour)
// ---------------------------------------------------------------------------------------------

private enum class SlotType { CELL, GAP }

private fun coordToSlot(px: Float, cell: Float, gap: Float, count: Int): Pair<Int, SlotType> {
    val stride = cell + gap
    val index = (px / stride).toInt().coerceIn(0, count - 1)
    val offset = px - index * stride
    return if (offset <= cell) index to SlotType.CELL else index to SlotType.GAP
}

// ---------------------------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------------------------

/** Raised, rounded board: soft drop shadow, wooden frame gradient and a faint inner glow. */
private fun DrawScope.drawBoardFrame(c: GamePalette) {
    val radius = Dimens.boardRadius.toPx()
    // Layered translucent rects fake a blurred drop shadow.
    for (i in 6 downTo 1) {
        val spread = i * 2.2.dp.toPx()
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.07f),
            topLeft = Offset(-spread / 2, 4.dp.toPx() - spread / 2 + i),
            size = Size(size.width + spread, size.height + spread),
            cornerRadius = CornerRadius(radius + spread / 2),
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(c.boardFrameEdge, c.boardFrame)),
        cornerRadius = CornerRadius(radius),
    )
    // Inner glow / bevel
    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
        cornerRadius = CornerRadius(radius - 1.dp.toPx()),
        style = Stroke(1.5.dp.toPx()),
    )
}

private fun DrawScope.drawTiles(m: Metrics, c: GamePalette, goalRowMe: Int, goalRowOpp: Int) {
    val tileRadius = Dimens.tileRadius.toPx()
    // Dark base: whatever the tiles don't cover reads as the groove a wall slides into.
    drawRoundRect(c.groove, cornerRadius = CornerRadius(tileRadius * 1.4f))
    val corner = CornerRadius(tileRadius)
    for (r in 0 until CELLS) {
        for (col in 0 until CELLS) {
            val left = col * m.stride
            val top = r * m.stride
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(c.tileTop, c.tileBottom), startY = top, endY = top + m.cell),
                topLeft = Offset(left, top),
                size = Size(m.cell, m.cell),
                cornerRadius = corner,
            )
            // Top highlight gives each tile a slightly raised look.
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(left + 0.5f, top + 0.5f),
                size = Size(m.cell - 1f, m.cell - 1f),
                cornerRadius = corner,
                style = Stroke(1f),
            )
        }
    }
    // Goal rows: tinted with their owner's colour, plus a finish flag at both ends.
    for ((row, color) in listOf(goalRowMe to c.player, goalRowOpp to c.opponent)) {
        val top = row * m.stride
        for (col in 0 until CELLS) {
            drawRoundRect(
                color = color.copy(alpha = 0.30f),
                topLeft = Offset(col * m.stride, top),
                size = Size(m.cell, m.cell),
                cornerRadius = corner,
            )
        }
        drawFlag(Offset(m.cell * 0.18f, top + m.cell * 0.14f), m.cell * 0.5f, color.darken(0.1f))
        drawFlag(Offset(8 * m.stride + m.cell * 0.18f, top + m.cell * 0.14f), m.cell * 0.5f, color.darken(0.1f))
    }
}

/** A small pennant on a pole; [origin] is the pole's top-left, [h] its height. */
private fun DrawScope.drawFlag(origin: Offset, h: Float, color: Color) {
    val pole = h * 0.09f
    drawRoundRect(color, origin, Size(pole, h), CornerRadius(pole / 2))
    val flag = Path().apply {
        moveTo(origin.x + pole, origin.y)
        lineTo(origin.x + pole + h * 0.62f, origin.y + h * 0.22f)
        lineTo(origin.x + pole, origin.y + h * 0.46f)
        close()
    }
    drawPath(flag, color)
}

private data class WallRect(val topLeft: Offset, val size: Size)

/** Wall at notch (row, col): horizontal ones fill the gutter below [row] over columns col..col+1, vertical the gutter right of [col]. */
private fun wallRect(w: WallPos, horizontal: Boolean, m: Metrics): WallRect =
    if (horizontal) WallRect(Offset(w.col * m.stride, w.row * m.stride + m.cell), Size(2 * m.cell + m.gap, m.gap))
    else WallRect(Offset(w.col * m.stride + m.cell, w.row * m.stride), Size(m.gap, 2 * m.cell + m.gap))

/** A solid wall piece in the owner's colour. [settle] 0→1 plays the drop-in (grows from a thick, faint bar and settles). */
private fun DrawScope.drawWallPiece(w: WallPos, horizontal: Boolean, m: Metrics, color: Color, settle: Float) {
    val r = wallRect(w, horizontal, m)
    // While settling the bar is fatter and fainter, like a piece being lowered into place.
    val grow = 1f + (1f - settle).coerceIn(0f, 1f) * 0.9f
    val thick = (if (horizontal) r.size.height else r.size.width) * grow
    val center = if (horizontal) r.topLeft.y + r.size.height / 2 else r.topLeft.x + r.size.width / 2
    val length = if (horizontal) r.size.width else r.size.height
    val start = if (horizontal) r.topLeft.x else r.topLeft.y
    val tl = if (horizontal) Offset(start, center - thick / 2) else Offset(center - thick / 2, start)
    val size = if (horizontal) Size(length, thick) else Size(thick, length)
    val radius = CornerRadius(min(size.width, size.height) / 2)
    val alpha = settle.coerceIn(0f, 1f)
    val shadowShift = m.gap * 0.35f * alpha

    drawRoundRect(Color.Black.copy(alpha = 0.40f * alpha), tl + Offset(shadowShift * 0.5f, shadowShift), size, radius)
    val brush = if (horizontal)
        Brush.verticalGradient(listOf(color.lighten(0.4f), color, color.darken(0.3f)), startY = tl.y, endY = tl.y + size.height)
    else
        Brush.horizontalGradient(listOf(color.lighten(0.4f), color, color.darken(0.3f)), startX = tl.x, endX = tl.x + size.width)
    drawRoundRect(brush, tl, size, radius, alpha = 0.35f + 0.65f * alpha)
}

/** Translucent preview/invalid wall: soft fill, wide faint glow and a dashed outline. */
private fun DrawScope.drawWallGhost(
    w: WallPos, horizontal: Boolean, m: Metrics, color: Color, fillAlpha: Float, outlineAlpha: Float,
) {
    val r = wallRect(w, horizontal, m)
    val radius = CornerRadius(min(r.size.width, r.size.height) / 2)
    drawRoundRect(color.copy(alpha = 0.25f * outlineAlpha), r.topLeft - Offset(3f, 3f), Size(r.size.width + 6f, r.size.height + 6f), radius, style = Stroke(6f))
    drawRoundRect(color.copy(alpha = fillAlpha), r.topLeft, r.size, radius)
    drawRoundRect(
        Color.White.copy(alpha = outlineAlpha), r.topLeft, r.size, radius,
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
    )
}

private enum class Glyph { PERSON, ROBOT, DIAMOND }

/** A glossy 3-D token. [breathe] 0..1 scales the active player's pawn; [hop] lifts it off its shadow. */
private fun DrawScope.drawPawn(
    m: Metrics, pos: Offset, hop: Float, base: Color, dark: Color, glyph: Glyph, breathe: Float, active: Boolean,
) {
    val ground = Offset(pos.x * m.stride + m.cell / 2, pos.y * m.stride + m.cell / 2)
    val lift = hop * m.cell
    val center = ground - Offset(0f, lift)
    val r = m.cell * 0.36f * (1f + 0.07f * breathe)

    // Shadow stays on the ground and fades while the pawn is in the air.
    val shadowFade = 1f - (lift / m.cell).coerceIn(0f, 0.8f)
    drawOval(
        Color.Black.copy(alpha = 0.35f * shadowFade),
        topLeft = Offset(ground.x - r * 0.9f, ground.y + r * 0.55f),
        size = Size(r * 1.8f, r * 0.55f),
    )
    if (active) {
        drawCircle(base.copy(alpha = 0.22f + 0.18f * breathe), r * 1.55f, center)
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(base.lighten(0.55f), base, dark),
            center = center + Offset(-r * 0.3f, -r * 0.35f),
            radius = r * 1.5f,
        ),
        radius = r,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.5f), r, center, style = Stroke(1.2.dp.toPx()))
    // Specular highlight
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.2f, center + Offset(-r * 0.38f, -r * 0.45f))

    val ink = Color.White.copy(alpha = 0.95f)
    when (glyph) {
        Glyph.PERSON -> {
            drawCircle(ink, r * 0.24f, center + Offset(0f, -r * 0.22f))
            drawArc(ink, 180f, 180f, true, center + Offset(-r * 0.42f, r * 0.12f), Size(r * 0.84f, r * 0.62f))
        }
        Glyph.ROBOT -> {
            drawRoundRect(ink, center + Offset(-r * 0.42f, -r * 0.2f), Size(r * 0.84f, r * 0.58f), CornerRadius(r * 0.14f))
            drawCircle(dark, r * 0.1f, center + Offset(-r * 0.18f, r * 0.09f))
            drawCircle(dark, r * 0.1f, center + Offset(r * 0.18f, r * 0.09f))
            drawRoundRect(ink, center + Offset(-r * 0.04f, -r * 0.5f), Size(r * 0.08f, r * 0.3f))
            drawCircle(ink, r * 0.1f, center + Offset(0f, -r * 0.52f))
        }
        Glyph.DIAMOND -> {
            val d = Path().apply {
                moveTo(center.x, center.y - r * 0.5f); lineTo(center.x + r * 0.42f, center.y)
                lineTo(center.x, center.y + r * 0.5f); lineTo(center.x - r * 0.42f, center.y); close()
            }
            drawPath(d, ink)
        }
    }
}
