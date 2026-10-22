package com.blockade.quoridor

import com.blockade.quoridor.domain.ai.AiDifficulty
import com.blockade.quoridor.domain.ai.QuoridorAi
import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.WallPos
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.domain.rules.QuoridorRules.Action
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuoridorAiTest {

    private fun play(a0: (GameState) -> Action, a1: (GameState) -> Action, maxPlies: Int = 300): GameState {
        var s = QuoridorRules.initialState(0)
        var plies = 0
        while (s.status == "playing" && plies++ < maxPlies) {
            val who = s.currentTurn
            val action = if (who == 0) a0(s) else a1(s)
            s = QuoridorRules.apply(s, who, action) ?: throw AssertionError("illegal action by player $who: $action")
        }
        return s
    }

    private fun randomPlayer(seed: Int): (GameState) -> Action {
        val rng = Random(seed)
        return { s ->
            val me = s.currentTurn
            val walls = if (s.players[me].wallsLeft > 0 && rng.nextInt(4) == 0)
                (0 until 8).flatMap { r -> (0 until 8).flatMap { c -> listOf(true, false).map { Action.Wall(r, c, it) } } }
                    .filter { QuoridorRules.isWallValid(s, it.row, it.col, it.horizontal, me) }
            else emptyList()
            if (walls.isNotEmpty()) walls.random(rng)
            else QuoridorRules.getValidMoves(s, me).random(rng).let { Action.Move(it.row, it.col) }
        }
    }

    // ------------------------------------------------------------------
    // apply()
    // ------------------------------------------------------------------
    @Test fun `apply rejects an illegal move and a move out of turn`() {
        val s = QuoridorRules.initialState(0)
        assertNull(QuoridorRules.apply(s, 0, Action.Move(5, 4)))
        assertNull(QuoridorRules.apply(s, 1, Action.Move(1, 4)))
    }

    @Test fun `apply moves the pawn and passes the turn`() {
        val s = QuoridorRules.apply(QuoridorRules.initialState(0), 0, Action.Move(7, 4))!!
        assertEquals(7, s.players[0].row)
        assertEquals(1, s.currentTurn)
    }

    @Test fun `apply wall spends a wall and rejects a conflicting one`() {
        val s = QuoridorRules.apply(QuoridorRules.initialState(0), 0, Action.Wall(3, 3, true))!!
        assertEquals(9, s.players[0].wallsLeft)
        assertEquals(listOf(WallPos(3, 3)), s.walls.horizontal)
        assertNull(QuoridorRules.apply(s, 1, Action.Wall(3, 4, true)))
    }

    @Test fun `reaching the goal row finishes the game`() {
        val base = QuoridorRules.initialState(0)
        val s = base.copy(players = listOf(base.players[0].copy(row = 1, col = 2), base.players[1].copy(col = 8)))
        val done = QuoridorRules.apply(s, 0, Action.Move(0, 2))!!
        assertEquals("finished", done.status)
        assertEquals(0, done.winner)
    }

    @Test fun `shortest path on an empty board is straight`() {
        assertEquals(9, QuoridorRules.shortestPath(QuoridorRules.initialState(), 0)!!.size)
    }

    // ------------------------------------------------------------------
    // AI
    // ------------------------------------------------------------------
    @Test fun `every difficulty only plays legal actions in a full game`() {
        for (d in AiDifficulty.entries) {
            val ai = QuoridorAi(d, Random(1))
            play({ ai.choose(it, 0) }, randomPlayer(7))
        }
    }

    @Test fun `ai takes the winning move`() {
        val base = QuoridorRules.initialState(1)
        val s = base.copy(players = listOf(base.players[0].copy(col = 8), base.players[1].copy(row = 7, col = 2)))
        for (d in AiDifficulty.entries) {
            val move = QuoridorAi(d, Random(3)).choose(s.copy(), 1)
            if (d != AiDifficulty.EASY) assertEquals(Action.Move(8, 2), move)
        }
    }

    @Test fun `hard beats a random player from either seat`() {
        val hard = QuoridorAi(AiDifficulty.HARD, Random(5))
        val asP0 = play({ hard.choose(it, 0) }, randomPlayer(11))
        assertEquals(0, asP0.winner)
        val asP1 = play(randomPlayer(12), { hard.choose(it, 1) })
        assertEquals(1, asP1.winner)
    }

    @Test fun `medium beats a random player`() {
        val medium = QuoridorAi(AiDifficulty.MEDIUM, Random(9))
        assertEquals(0, play({ medium.choose(it, 0) }, randomPlayer(21)).winner)
    }

    @Test fun `hard beats easy`() {
        val hard = QuoridorAi(AiDifficulty.HARD, Random(2))
        val easy = QuoridorAi(AiDifficulty.EASY, Random(2))
        assertEquals(0, play({ hard.choose(it, 0) }, { easy.choose(it, 1) }).winner)
    }

    @Test fun `hard picks a move quickly`() {
        val hard = QuoridorAi(AiDifficulty.HARD, Random(4))
        val s = QuoridorRules.initialState(0)
        val start = System.nanoTime()
        hard.choose(s, 0)
        assertTrue("took too long", (System.nanoTime() - start) / 1_000_000 < 3000)
    }
}
