package com.blockade.quoridor.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.WallPos
import com.blockade.quoridor.domain.rules.QuoridorRules

private const val CELLS = QuoridorRules.BOARD   // 9
private const val WALL_GRID = QuoridorRules.WALL_GRID // 8
private const val GAP_FRACTION = 0.08f          // gap is 8% of cell size

@Composable
fun QuoridorBoard(
    state: GameState,
    uiState: GameUiState,
    onCellTap: (row: Int, col: Int) -> Unit,
    onWallSlotTap: (wr: Int, wc: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val p0Color = MaterialTheme.colorScheme.tertiary
    val p1Color = MaterialTheme.colorScheme.secondary
    val moveHighlight = primary.copy(alpha = 0.35f)
    val wallColor = MaterialTheme.colorScheme.error
    val previewColor = wallColor.copy(alpha = 0.5f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(uiState) {
                detectTapGestures { offset ->
                    val total = size.width.toFloat()
                    val gapPx = total / (CELLS + (CELLS - 1) * GAP_FRACTION)
                    val gap = gapPx * GAP_FRACTION
                    val cell = gapPx

                    // Determine what was tapped
                    val (tapRow, tapType) = coordToSlot(offset.y, cell, gap, CELLS)
                    val (tapCol, tapTypeC) = coordToSlot(offset.x, cell, gap, CELLS)

                    if (tapType == SlotType.CELL && tapTypeC == SlotType.CELL) {
                        onCellTap(tapRow, tapCol)
                    } else if (tapType == SlotType.GAP || tapTypeC == SlotType.GAP) {
                        // Wall slot: use the notch grid index
                        val wr = if (tapType == SlotType.GAP) tapRow else if (tapRow == CELLS - 1) tapRow - 1 else tapRow
                        val wc = if (tapTypeC == SlotType.GAP) tapCol else if (tapCol == CELLS - 1) tapCol - 1 else tapCol
                        if (wr in 0 until WALL_GRID && wc in 0 until WALL_GRID) {
                            onWallSlotTap(wr, wc)
                        }
                    }
                }
            },
    ) {
        val total = size.width
        val gap = total / (CELLS + (CELLS - 1) * GAP_FRACTION) * GAP_FRACTION
        val cell = (total - gap * (CELLS - 1)) / CELLS

        // Draw cells
        for (r in 0 until CELLS) {
            for (c in 0 until CELLS) {
                val left = c * (cell + gap)
                val top = r * (cell + gap)
                val isHighlighted = uiState.validMoves.contains(QuoridorRules.Pos(r, c))
                drawRoundRect(
                    color = if (isHighlighted) moveHighlight else surface,
                    topLeft = Offset(left, top),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(4f),
                )
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(left, top),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(4f),
                    style = Stroke(width = 1f),
                )
            }
        }

        // Draw goal markers (top row goal for p0, bottom row goal for p1)
        drawGoalRow(0, p0Color.copy(alpha = 0.15f), cell, gap)
        drawGoalRow(8, p1Color.copy(alpha = 0.15f), cell, gap)

        // Draw placed walls
        for (w in state.walls.horizontal) drawWall(w, horizontal = true, cell, gap, wallColor)
        for (w in state.walls.vertical) drawWall(w, horizontal = false, cell, gap, wallColor)

        // Draw preview wall
        uiState.wallPreview?.let { p ->
            drawWall(WallPos(p.row, p.col), horizontal = p.horizontal, cell, gap, previewColor)
        }

        // Draw pawns
        state.players.forEachIndexed { i, ps ->
            val cx = ps.col * (cell + gap) + cell / 2f
            val cy = ps.row * (cell + gap) + cell / 2f
            val r = cell * 0.35f
            drawCircle(color = if (i == 0) p0Color else p1Color, radius = r, center = Offset(cx, cy))
            drawCircle(color = Color.White.copy(alpha = 0.6f), radius = r * 0.45f, center = Offset(cx, cy))
        }
    }
}

private enum class SlotType { CELL, GAP }

private fun coordToSlot(px: Float, cell: Float, gap: Float, count: Int): Pair<Int, SlotType> {
    val stride = cell + gap
    val index = (px / stride).toInt().coerceIn(0, count - 1)
    val offset = px - index * stride
    return if (offset <= cell) index to SlotType.CELL else index to SlotType.GAP
}

private fun DrawScope.drawGoalRow(row: Int, color: Color, cell: Float, gap: Float) {
    val top = row * (cell + gap)
    for (c in 0 until CELLS) {
        drawRect(color = color, topLeft = Offset(c * (cell + gap), top), size = Size(cell, cell))
    }
}

private fun DrawScope.drawWall(w: WallPos, horizontal: Boolean, cell: Float, gap: Float, color: Color) {
    val wallThickness = gap * 0.85f
    if (horizontal) {
        // Horizontal wall at notch (w.row, w.col): spans from col w.col to w.col+1 in the gap below row w.row
        val left = w.col * (cell + gap)
        val top = w.row * (cell + gap) + cell + (gap - wallThickness) / 2f
        val width = 2 * cell + gap  // spans 2 cells + 1 gap between them
        drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(width, wallThickness), cornerRadius = CornerRadius(wallThickness / 2))
    } else {
        // Vertical wall at notch (w.row, w.col): spans from row w.row to w.row+1 in the gap right of col w.col
        val left = w.col * (cell + gap) + cell + (gap - wallThickness) / 2f
        val top = w.row * (cell + gap)
        val height = 2 * cell + gap
        drawRoundRect(color = color, topLeft = Offset(left, top), size = Size(wallThickness, height), cornerRadius = CornerRadius(wallThickness / 2))
    }
}
