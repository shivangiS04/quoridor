package com.blockade.quoridor.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp as lerpOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CELLS = QuoridorRules.BOARD            // 9
private const val WALL_GRID = QuoridorRules.WALL_GRID    // 8
private const val GAP_FRACTION = 0.14f                   // gutter = 14% of a cell

/** How far above the fingertip a dragged wall is held, so it stays visible. */
internal val WALL_DRAG_LIFT = 48.dp

/** Cell/gutter size in px for a square canvas of [total] px. */
private class Metrics(total: Float) {
    val gap = total / (CELLS + (CELLS - 1) * GAP_FRACTION) * GAP_FRACTION
    val cell = (total - gap * (CELLS - 1)) / CELLS
    val stride = cell + gap
}

/** Size of a wall piece on a board [boardSidePx] wide. */
internal fun wallBarSize(boardSidePx: Float, horizontal: Boolean): Size {
    val m = Metrics(boardSidePx)
    val length = 2 * m.cell + m.gap
    val thick = m.gap * 1.5f
    return if (horizontal) Size(length, thick) else Size(thick, length)
}

private data class DragTarget(val wr: Int, val wc: Int, val valid: Boolean)

/**
 * The board: flat tiles, flat pawns, flat wall bars. Drawn as three stacked layers so that animation only
 * redraws what moved:
 *   1. tiles and goal rows (static)
 *   2. walls, previews, the wall-drag target and the illegal-placement flash
 *   3. move hints, pawns and tap handling
 */
@Composable
fun QuoridorBoard(
    state: GameState,
    uiState: GameUiState,
    drag: WallDragState,
    onCellTap: (row: Int, col: Int) -> Unit,
    onWallSlotTap: (wr: Int, wc: Int) -> Unit,
    onWallDrop: (wr: Int, wc: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GameTheme.colors
    val me = uiState.myPlayerIndex
    val onCell by rememberUpdatedState(onCellTap)
    val onWall by rememberUpdatedState(onWallSlotTap)
    val onDrop by rememberUpdatedState(onWallDrop)
    val stateNow by rememberUpdatedState(state)

    // ---- wall drag: where would the carried wall land? ----------------------------------------
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    var sidePx by remember { mutableFloatStateOf(0f) }
    val liftPx = with(androidx.compose.ui.platform.LocalDensity.current) { WALL_DRAG_LIFT.toPx() }
    val target by remember {
        derivedStateOf {
            if (!drag.active || sidePx <= 0f) return@derivedStateOf null
            val aim = drag.finger - rootTopLeft - Offset(0f, liftPx)
            // Only snap while the held wall is actually over the board.
            if (aim.x !in 0f..sidePx || aim.y !in 0f..sidePx) return@derivedStateOf null
            val m = Metrics(sidePx)
            val (wr, wc) = nearestNotch(aim.x, aim.y, m.cell, m.gap)
            DragTarget(wr, wc, QuoridorRules.isWallValid(stateNow, wr, wc, drag.horizontal, me))
        }
    }
    SideEffect {
        drag.targetValid = target?.valid
        drag.boardSidePx = sidePx
        drag.onRelease = { target?.let { onDrop(it.wr, it.wc) } }
    }

    // ---- animation state ---------------------------------------------------------------------
    val lastWall = uiState.lastWall
    val revision = uiState.snapshot?.revision
    val wallIn = remember { Animatable(1f) }
    LaunchedEffect(lastWall, revision) {
        if (lastWall != null) {
            wallIn.snapTo(0f)
            wallIn.animateTo(1f, tween(Motion.Standard))
        }
    }
    val shake = remember { Animatable(1f) }
    val invalid = uiState.invalidFlash
    val invalidAlpha = remember { Animatable(0f) }
    LaunchedEffect(invalid?.tick) {
        if (invalid != null) {
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
                translationX = sin(shake.value * PI.toFloat() * 5f) * (1f - shake.value) * 4f * density
            }
            .onGloballyPositioned {
                rootTopLeft = it.positionInRoot()
                sidePx = it.size.width.toFloat()
            },
    ) {
        // 1 — tiles and goal rows
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            drawTiles(Metrics(size.width), colors, QuoridorRules.goalRow(me), QuoridorRules.goalRow(1 - me))
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
                drawWallBar(w, true, m, colorFor(w, true), if (isNew) wallIn.value else 1f)
            }
            for (w in state.walls.vertical) {
                val isNew = lastWall != null && !lastWall.horizontal && lastWall.pos == w
                drawWallBar(w, false, m, colorFor(w, false), if (isNew) wallIn.value else 1f)
            }
            uiState.wallPreview?.let { p ->
                drawWallGhost(WallPos(p.row, p.col), p.horizontal, m, colors.player, fillAlpha = 0.4f, outlineAlpha = 0.9f)
            }
            target?.let { t ->
                drawWallGhost(
                    WallPos(t.wr, t.wc), drag.horizontal, m, if (t.valid) colors.player else colors.invalid,
                    fillAlpha = 0.4f, outlineAlpha = 0.9f,
                )
            }
            if (invalid != null && invalidAlpha.value > 0.01f) {
                drawWallGhost(
                    WallPos(invalid.row, invalid.col), invalid.horizontal, m, colors.invalid,
                    fillAlpha = 0.5f * invalidAlpha.value, outlineAlpha = invalidAlpha.value,
                )
            }
        }

        // 3 — move hints, pawns, taps
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
            // Cells you can move to: a thin white outline.
            val hint = Stroke(2.dp.toPx())
            val radius = CornerRadius(Dimens.tileRadius.toPx())
            for (p in uiState.validMoves) {
                drawRoundRect(
                    colors.onSurface.copy(alpha = 0.9f),
                    topLeft = Offset(p.col * m.stride + 1f, p.row * m.stride + 1f),
                    size = Size(m.cell - 2f, m.cell - 2f),
                    cornerRadius = radius,
                    style = hint,
                )
            }
            drawPawn(m, myPawn.position, colors.player)
            drawPawn(m, oppPawn.position, colors.opponent)
        }
    }
}

/**
 * The wall notch (row, col) closest to a point on the board. Notch (wr, wc) sits where the gutters between
 * cells (wr..wr+1, wc..wc+1) cross, i.e. at x = wc*stride + cell + gap/2; points outside snap to the edge notch.
 */
internal fun nearestNotch(x: Float, y: Float, cell: Float, gap: Float): Pair<Int, Int> {
    val stride = cell + gap
    val col = ((x - cell - gap / 2) / stride).roundToInt().coerceIn(0, WALL_GRID - 1)
    val row = ((y - cell - gap / 2) / stride).roundToInt().coerceIn(0, WALL_GRID - 1)
    return row to col
}

// ---------------------------------------------------------------------------------------------
// Pawn motion: a plain slide
// ---------------------------------------------------------------------------------------------

private class PawnMotion(start: Offset) {
    val progress = Animatable(1f)
    var from by mutableStateOf(start)
    var to by mutableStateOf(start)

    /** Current position in cell units (x = column, y = row). */
    val position: Offset get() = lerpOffset(from, to, progress.value)

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
// Hit testing
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

private fun DrawScope.drawTiles(m: Metrics, c: GamePalette, goalRowMe: Int, goalRowOpp: Int) {
    val small = Dimens.tileRadius.toPx()
    val big = Dimens.boardCornerRadius.toPx()
    val border = Stroke(1.dp.toPx())
    for (r in 0 until CELLS) {
        for (col in 0 until CELLS) {
            // Only the four outer corners of the board get the larger radius.
            val shape = RoundRect(
                left = col * m.stride, top = r * m.stride,
                right = col * m.stride + m.cell, bottom = r * m.stride + m.cell,
                topLeftCornerRadius = CornerRadius(if (r == 0 && col == 0) big else small),
                topRightCornerRadius = CornerRadius(if (r == 0 && col == CELLS - 1) big else small),
                bottomLeftCornerRadius = CornerRadius(if (r == CELLS - 1 && col == 0) big else small),
                bottomRightCornerRadius = CornerRadius(if (r == CELLS - 1 && col == CELLS - 1) big else small),
            )
            val path = Path().apply { addRoundRect(shape) }
            drawPath(path, c.tile)
            // Each goal row carries a faint tint of the colour of the pawn that is heading there.
            when (r) {
                goalRowMe -> drawPath(path, c.player.copy(alpha = 0.08f))
                goalRowOpp -> drawPath(path, c.opponent.copy(alpha = 0.08f))
            }
            drawPath(path, c.tileBorder, style = border)
        }
    }
}

private data class BarRect(val topLeft: Offset, val size: Size)

/** Wall at notch (row, col): horizontal ones sit in the gutter below [row] over columns col..col+1, vertical ones right of [col]. */
private fun barRect(w: WallPos, horizontal: Boolean, m: Metrics): BarRect {
    val length = 2 * m.cell + m.gap
    val thick = m.gap * 1.5f
    val offset = (thick - m.gap) / 2   // the bar is a little thicker than the gutter, centred on it
    return if (horizontal) BarRect(Offset(w.col * m.stride, w.row * m.stride + m.cell - offset), Size(length, thick))
    else BarRect(Offset(w.col * m.stride + m.cell - offset, w.row * m.stride), Size(thick, length))
}

/** A placed wall: a flat rounded bar in its owner's colour. [fade] 0→1 fades a newly placed wall in. */
private fun DrawScope.drawWallBar(w: WallPos, horizontal: Boolean, m: Metrics, color: Color, fade: Float) {
    val r = barRect(w, horizontal, m)
    drawRoundRect(color, r.topLeft, r.size, CornerRadius(min(r.size.width, r.size.height) / 2), alpha = fade.coerceIn(0f, 1f))
}

/** A translucent wall with a dashed outline: the tap preview, the drag target, and the illegal-placement flash. */
private fun DrawScope.drawWallGhost(
    w: WallPos, horizontal: Boolean, m: Metrics, color: Color, fillAlpha: Float, outlineAlpha: Float,
) {
    val r = barRect(w, horizontal, m)
    val radius = CornerRadius(min(r.size.width, r.size.height) / 2)
    drawRoundRect(color.copy(alpha = fillAlpha), r.topLeft, r.size, radius)
    drawRoundRect(
        Color.White.copy(alpha = outlineAlpha), r.topLeft, r.size, radius,
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))),
    )
}

/** A flat solid disc. */
private fun DrawScope.drawPawn(m: Metrics, pos: Offset, color: Color) {
    drawCircle(
        color = color,
        radius = m.cell * 0.30f,
        center = Offset(pos.x * m.stride + m.cell / 2, pos.y * m.stride + m.cell / 2),
    )
}
