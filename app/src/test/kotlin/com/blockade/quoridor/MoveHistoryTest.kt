package com.blockade.quoridor

import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.PlayerState
import com.blockade.quoridor.domain.model.WallPos
import com.blockade.quoridor.domain.model.WallsState
import com.blockade.quoridor.domain.rules.MoveHistory
import org.junit.Assert.*
import org.junit.Test

class MoveHistoryTest {

    private fun state(
        p0: Pair<Int, Int> = 8 to 4,
        p1: Pair<Int, Int> = 0 to 4,
        turn: Int = 0,
        hw: List<WallPos> = emptyList(),
        vw: List<WallPos> = emptyList(),
        status: String = "playing",
        winner: Int? = null,
    ) = GameState(
        players = listOf(PlayerState(p0.first, p0.second, 10), PlayerState(p1.first, p1.second, 10)),
        walls = WallsState(hw, vw),
        currentTurn = turn,
        status = status,
        winner = winner,
    )

    @Test fun `pawn move is described with chess-like squares`() {
        val rec = MoveHistory.describe(state(), state(p0 = 7 to 4, turn = 1))!!
        assertEquals(0, rec.player)
        assertEquals("e1 → e2", rec.text)
        assertNull(rec.wall)
    }

    @Test fun `player 1 move is attributed to player 1`() {
        val rec = MoveHistory.describe(state(turn = 1), state(p1 = 1 to 4, turn = 0))!!
        assertEquals(1, rec.player)
        assertEquals("e9 → e8", rec.text)
    }

    @Test fun `horizontal wall is detected and reports its position`() {
        val rec = MoveHistory.describe(state(), state(turn = 1, hw = listOf(WallPos(3, 2))))!!
        assertEquals(0, rec.player)
        assertTrue(rec.text.startsWith("Wall c5"))
        assertEquals(WallPos(3, 2), rec.wall!!.pos)
        assertTrue(rec.wall!!.horizontal)
    }

    @Test fun `vertical wall is detected`() {
        val rec = MoveHistory.describe(state(), state(turn = 1, vw = listOf(WallPos(0, 7))))!!
        assertFalse(rec.wall!!.horizontal)
        assertEquals("Wall h8 (vertical)", rec.text)
    }

    @Test fun `resignation is a game-end record credited to the loser`() {
        val rec = MoveHistory.describe(state(turn = 1), state(turn = 1, status = "finished", winner = 1))!!
        assertTrue(rec.gameEnd)
        assertEquals(0, rec.player)
    }

    @Test fun `identical states produce no record`() {
        assertNull(MoveHistory.describe(state(), state()))
    }
}
