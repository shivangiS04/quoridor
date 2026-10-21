package com.blockade.quoridor.domain.ai

import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.rules.QuoridorRules
import com.blockade.quoridor.domain.rules.QuoridorRules.Action
import com.blockade.quoridor.domain.rules.QuoridorRules.Pos
import kotlin.random.Random

enum class AiDifficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
}

/**
 * Computer opponent.
 * - EASY: walks its shortest path, but a quarter of the time plays a random legal action.
 * - MEDIUM: picks the action with the best position score one ply ahead.
 * - HARD: alpha-beta search three plies deep with the same score.
 * Position score = (opponent's path length − own path length) × 10 + wall-count difference.
 */
class QuoridorAi(
    private val difficulty: AiDifficulty,
    private val random: Random = Random.Default,
) {

    /** Chooses an action for [me], who must be the player to move in [state]. */
    fun choose(state: GameState, me: Int): Action = when (difficulty) {
        AiDifficulty.EASY -> chooseEasy(state, me)
        AiDifficulty.MEDIUM -> chooseBySearch(state, me, depth = 1)
        AiDifficulty.HARD -> chooseBySearch(state, me, depth = 3)
    }

    // ------------------------------------------------------------------
    // Easy
    // ------------------------------------------------------------------
    private fun chooseEasy(s: GameState, me: Int): Action {
        val candidates = candidates(s, me)
        if (random.nextDouble() < 0.25) return candidates.random(random)
        return stepAlongPath(s, me) ?: candidates.first()
    }

    /** The legal pawn move that gets closest to the goal. */
    private fun stepAlongPath(s: GameState, me: Int): Action.Move? =
        QuoridorRules.getValidMoves(s, me)
            .minByOrNull { pathLength(s.withPawn(me, it), me) }
            ?.let { Action.Move(it.row, it.col) }

    // ------------------------------------------------------------------
    // Medium / Hard
    // ------------------------------------------------------------------
    private fun chooseBySearch(s: GameState, me: Int, depth: Int): Action {
        var best = emptyList<Action>()
        var bestScore = Int.MIN_VALUE
        for (action in candidates(s, me)) {
            val next = QuoridorRules.apply(s, me, action) ?: continue
            val score = search(next, me, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE)
            if (score > bestScore) { bestScore = score; best = listOf(action) }
            else if (score == bestScore) best = best + action
        }
        return best.random(random)
    }

    private fun search(s: GameState, me: Int, depth: Int, alphaIn: Int, betaIn: Int): Int {
        if (s.status == "finished") return if (s.winner == me) WIN + depth else -WIN - depth
        if (depth == 0) return evaluate(s, me)
        var alpha = alphaIn
        var beta = betaIn
        val maximizing = s.currentTurn == me
        var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        for (action in candidates(s, s.currentTurn)) {
            val next = QuoridorRules.apply(s, s.currentTurn, action) ?: continue
            val score = search(next, me, depth - 1, alpha, beta)
            if (maximizing) { best = maxOf(best, score); alpha = maxOf(alpha, best) }
            else { best = minOf(best, score); beta = minOf(beta, best) }
            if (alpha >= beta) break
        }
        return best
    }

    private fun evaluate(s: GameState, me: Int): Int {
        val opp = 1 - me
        return (pathLength(s, opp) - pathLength(s, me)) * 10 + (s.players[me].wallsLeft - s.players[opp].wallsLeft)
    }

    // ------------------------------------------------------------------
    // Move generation
    // ------------------------------------------------------------------

    /**
     * Every legal pawn move, plus the walls that cut an edge of the opponent's shortest path
     * (all other walls rarely matter and would make the search far slower).
     */
    private fun candidates(s: GameState, pi: Int): List<Action> {
        val actions = ArrayList<Action>()
        // Forward-looking moves first so alpha-beta prunes sooner.
        QuoridorRules.getValidMoves(s, pi)
            .sortedBy { pathLength(s.withPawn(pi, it), pi) }
            .mapTo(actions) { Action.Move(it.row, it.col) }

        if (s.players[pi].wallsLeft > 0) {
            val path = QuoridorRules.shortestPath(s, 1 - pi) ?: return actions
            val walls = LinkedHashSet<Action.Wall>()
            for ((a, b) in path.zipWithNext()) {
                if (a.row != b.row) { // vertical step: blocked by horizontal walls on the boundary
                    val wr = minOf(a.row, b.row)
                    walls += Action.Wall(wr, a.col, horizontal = true)
                    walls += Action.Wall(wr, a.col - 1, horizontal = true)
                } else {
                    val wc = minOf(a.col, b.col)
                    walls += Action.Wall(a.row, wc, horizontal = false)
                    walls += Action.Wall(a.row - 1, wc, horizontal = false)
                }
            }
            walls.filterTo(actions) {
                QuoridorRules.isWallValid(s, it.row, it.col, it.horizontal, pi)
            }
        }
        return actions
    }

    private fun pathLength(s: GameState, pi: Int): Int =
        (QuoridorRules.shortestPath(s, pi)?.size ?: 82) - 1

    private fun GameState.withPawn(pi: Int, to: Pos) =
        copy(players = players.mapIndexed { i, p -> if (i == pi) p.copy(row = to.row, col = to.col) else p })

    private companion object {
        const val WIN = 10_000
    }
}
