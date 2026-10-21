package com.blockade.quoridor.domain.rules

import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.WallPos

/** One entry in the move list, derived by diffing two consecutive game states. */
data class MoveRecord(
    val player: Int,
    val text: String,
    /** Set when the move placed a wall, so the board can animate it in. */
    val wall: PlacedWall? = null,
    /** True for resignations/timeouts, which change status without changing the board. */
    val gameEnd: Boolean = false,
)

data class PlacedWall(val pos: WallPos, val horizontal: Boolean)

object MoveHistory {

    /** Squares are named like chess: files a–i left to right, ranks 1–9 from player 0's side. */
    fun square(row: Int, col: Int) = "${'a' + col}${QuoridorRules.BOARD - row}"

    /** Wall notches are named by the file of their left/top cell and the rank line they sit on (1–8). */
    private fun notch(w: WallPos) = "${'a' + w.col}${QuoridorRules.WALL_GRID - w.row}"

    /** Describes what changed between [old] and [new], or null if nothing did. */
    fun describe(old: GameState, new: GameState): MoveRecord? {
        val mover = old.currentTurn
        val before = old.players[mover]
        val after = new.players[mover]
        if (before.row != after.row || before.col != after.col) {
            return MoveRecord(mover, "${square(before.row, before.col)} → ${square(after.row, after.col)}")
        }
        (new.walls.horizontal - old.walls.horizontal.toSet()).firstOrNull()?.let {
            return MoveRecord(mover, "Wall ${notch(it)} (horizontal)", PlacedWall(it, true))
        }
        (new.walls.vertical - old.walls.vertical.toSet()).firstOrNull()?.let {
            return MoveRecord(mover, "Wall ${notch(it)} (vertical)", PlacedWall(it, false))
        }
        if (old.status != "finished" && new.status == "finished") {
            // Either side can end the game off-turn, so credit the winner's opponent with the exit.
            val loser = 1 - (new.winner ?: mover)
            return MoveRecord(loser, "Game ended (resigned or timed out)", gameEnd = true)
        }
        return null
    }
}
