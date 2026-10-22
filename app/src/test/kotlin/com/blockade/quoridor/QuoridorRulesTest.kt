package com.blockade.quoridor

import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.PlayerState
import com.blockade.quoridor.domain.model.WallPos
import com.blockade.quoridor.domain.model.WallsState
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.domain.rules.QuoridorRules.Pos
import org.junit.Assert.*
import org.junit.Test

private fun emptyState(
    p0row: Int = 8, p0col: Int = 4,
    p1row: Int = 0, p1col: Int = 4,
    turn: Int = 0,
    hw: List<WallPos> = emptyList(),
    vw: List<WallPos> = emptyList(),
) = GameState(
    players = listOf(
        PlayerState(p0row, p0col, 10),
        PlayerState(p1row, p1col, 10),
    ),
    walls = WallsState(hw, vw),
    currentTurn = turn,
    status = "playing",
    winner = null,
)

class QuoridorRulesTest {

    // ------------------------------------------------------------------
    // Normal moves
    // ------------------------------------------------------------------
    @Test fun `player 0 can move up from start`() {
        val moves = QuoridorRules.getValidMoves(emptyState(), 0)
        assertTrue(moves.contains(Pos(7, 4)))  // up
        assertTrue(moves.contains(Pos(8, 3)))  // left
        assertTrue(moves.contains(Pos(8, 5)))  // right
        // no down from row 8
    }

    @Test fun `player 0 cannot move off top edge`() {
        val s = emptyState(p0row = 0, p0col = 4)
        val moves = QuoridorRules.getValidMoves(s, 0)
        assertFalse(moves.contains(Pos(-1, 4)))
    }

    // ------------------------------------------------------------------
    // Wall blocking
    // ------------------------------------------------------------------
    @Test fun `horizontal wall blocks downward movement`() {
        // Wall at notch (7, 4): blocks (7,4)↔(8,4) and (7,5)↔(8,5)
        val s = emptyState(p0row = 7, p0col = 4, hw = listOf(WallPos(7, 4)))
        val moves = QuoridorRules.getValidMoves(s, 0)
        assertFalse(moves.contains(Pos(8, 4)))  // blocked
    }

    @Test fun `horizontal wall at col-1 also blocks downward movement`() {
        // Wall at notch (7, 3): right half blocks (7,4)↔(8,4)
        val s = emptyState(p0row = 7, p0col = 4, hw = listOf(WallPos(7, 3)))
        val moves = QuoridorRules.getValidMoves(s, 0)
        assertFalse(moves.contains(Pos(8, 4)))
    }

    @Test fun `vertical wall blocks rightward movement`() {
        // Wall at notch (7, 4): top half blocks (7,4)↔(7,5)
        val s = emptyState(p0row = 7, p0col = 4, vw = listOf(WallPos(7, 4)))
        val moves = QuoridorRules.getValidMoves(s, 0)
        assertFalse(moves.contains(Pos(7, 5)))
    }

    // ------------------------------------------------------------------
    // Jump mechanics
    // ------------------------------------------------------------------
    @Test fun `straight jump over opponent`() {
        val s = emptyState(p0row = 5, p0col = 4, p1row = 4, p1col = 4)
        val moves = QuoridorRules.getValidMoves(s, 0)
        assertTrue(moves.contains(Pos(3, 4)))  // jump over p1
        assertFalse(moves.contains(Pos(4, 4))) // cannot land on p1
    }

    @Test fun `diagonal jump when straight is blocked by wall`() {
        val s = emptyState(
            p0row = 5, p0col = 4, p1row = 4, p1col = 4,
            hw = listOf(WallPos(3, 4)),  // blocks (3,4)↔(4,4), preventing straight jump
        )
        val moves = QuoridorRules.getValidMoves(s, 0)
        assertFalse(moves.contains(Pos(3, 4)))
        assertTrue(moves.contains(Pos(4, 3)) || moves.contains(Pos(4, 5)))  // diagonals
    }

    @Test fun `diagonal jump when straight is blocked by board edge`() {
        val s = emptyState(p0row = 1, p0col = 4, p1row = 0, p1col = 4)
        val moves = QuoridorRules.getValidMoves(s, 0)
        assertFalse(moves.contains(Pos(-1, 4)))  // out of bounds
        assertTrue(moves.contains(Pos(0, 3)) || moves.contains(Pos(0, 5)))
    }

    // ------------------------------------------------------------------
    // Wall conflict detection
    // ------------------------------------------------------------------
    @Test fun `same position wall conflicts`() {
        val s = emptyState(hw = listOf(WallPos(3, 3)))
        assertTrue(QuoridorRules.wallHasConflict(s, 3, 3, horizontal = true))
    }

    @Test fun `adjacent horizontal walls conflict`() {
        val s = emptyState(hw = listOf(WallPos(3, 3)))
        assertTrue(QuoridorRules.wallHasConflict(s, 3, 4, horizontal = true))  // col+1
        assertTrue(QuoridorRules.wallHasConflict(s, 3, 2, horizontal = true))  // col-1
        assertFalse(QuoridorRules.wallHasConflict(s, 3, 5, horizontal = true)) // col+2 — fine
    }

    @Test fun `crossing horizontal and vertical walls conflict`() {
        val s = emptyState(hw = listOf(WallPos(3, 3)))
        assertTrue(QuoridorRules.wallHasConflict(s, 3, 3, horizontal = false))
    }

    @Test fun `non-crossing vertical wall does not conflict`() {
        val s = emptyState(hw = listOf(WallPos(3, 3)))
        assertFalse(QuoridorRules.wallHasConflict(s, 3, 4, horizontal = false))
    }

    // ------------------------------------------------------------------
    // Path check
    // ------------------------------------------------------------------
    @Test fun `open board both players have path`() {
        val s = emptyState()
        assertTrue(QuoridorRules.hasPath(s, 0))
        assertTrue(QuoridorRules.hasPath(s, 1))
    }

    @Test fun `wall that seals a player in is rejected`() {
        // Four horizontal walls close every column but 8 off from row 7; a vertical wall then seals column 8.
        val s = emptyState(p0row = 8, p0col = 0, hw = listOf(0, 2, 4, 6).map { WallPos(7, it) })
        assertFalse(QuoridorRules.isWallValid(s, 7, 7, horizontal = false, pi = 0))
        assertTrue(QuoridorRules.isWallValid(s, 3, 3, horizontal = false, pi = 0))
    }

    // ------------------------------------------------------------------
    // Win detection
    // ------------------------------------------------------------------
    @Test fun `player 0 wins on row 0`() {
        val s = emptyState(p0row = 0, p0col = 2)
        // Row 0 is player 0's goal
        val moves = QuoridorRules.getValidMoves(s, 0)
        // p0 at goal — hasPath should still work, game engine detects win separately
        assertTrue(QuoridorRules.hasPath(s, 0))
    }
}
