package com.blockade.quoridor.domain.rules

import com.blockade.quoridor.domain.model.GameState
import com.blockade.quoridor.domain.model.PlayerState
import com.blockade.quoridor.domain.model.WallsState
import com.blockade.quoridor.domain.model.WallPos
import kotlin.math.abs

object QuoridorRules {

    const val BOARD = 9
    const val WALL_GRID = 8
    const val WALLS_PER_PLAYER = 10

    /** Mirrors TURN_TIMEOUT_MS in supabase/functions/_shared/quoridor.ts. */
    const val TURN_TIMEOUT_MS = 120_000L

    data class Pos(val row: Int, val col: Int)

    // ------------------------------------------------------------------
    // Wall-blocking helpers
    // Horizontal wall at notch (wr,wc):
    //   left half  → blocks (wr,wc)  ↔ (wr+1,wc)
    //   right half → blocks (wr,wc+1)↔ (wr+1,wc+1)
    // Vertical wall at notch (wr,wc):
    //   top half    → blocks (wr,wc)  ↔ (wr,wc+1)
    //   bottom half → blocks (wr+1,wc)↔ (wr+1,wc+1)
    // ------------------------------------------------------------------

    private fun isDownBlocked(s: GameState, r: Int, c: Int) =
        s.walls.horizontal.any { it.row == r && (it.col == c || it.col == c - 1) }

    private fun isUpBlocked(s: GameState, r: Int, c: Int) =
        s.walls.horizontal.any { it.row == r - 1 && (it.col == c || it.col == c - 1) }

    private fun isRightBlocked(s: GameState, r: Int, c: Int) =
        s.walls.vertical.any { it.col == c && (it.row == r || it.row == r - 1) }

    private fun isLeftBlocked(s: GameState, r: Int, c: Int) =
        s.walls.vertical.any { it.col == c - 1 && (it.row == r || it.row == r - 1) }

    fun isMovementBlocked(s: GameState, from: Pos, to: Pos): Boolean {
        val dr = to.row - from.row; val dc = to.col - from.col
        return when {
            dr == 1 && dc == 0 -> isDownBlocked(s, from.row, from.col)
            dr == -1 && dc == 0 -> isUpBlocked(s, from.row, from.col)
            dr == 0 && dc == 1 -> isRightBlocked(s, from.row, from.col)
            dr == 0 && dc == -1 -> isLeftBlocked(s, from.row, from.col)
            else -> true
        }
    }

    private fun inBounds(r: Int, c: Int) = r in 0 until BOARD && c in 0 until BOARD

    // ------------------------------------------------------------------
    // Valid pawn moves (orthogonal + jumps + diagonals)
    // ------------------------------------------------------------------
    fun getValidMoves(s: GameState, playerIndex: Int): List<Pos> {
        val p = s.players[playerIndex]
        val opp = s.players[1 - playerIndex]
        val moves = mutableListOf<Pos>()
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        for ((dr, dc) in dirs) {
            val nr = p.row + dr; val nc = p.col + dc
            if (!inBounds(nr, nc)) continue
            if (isMovementBlocked(s, Pos(p.row, p.col), Pos(nr, nc))) continue

            if (nr == opp.row && nc == opp.col) {
                val jr = nr + dr; val jc = nc + dc
                val straightOk = inBounds(jr, jc) &&
                    !isMovementBlocked(s, Pos(nr, nc), Pos(jr, jc))
                if (straightOk) {
                    moves += Pos(jr, jc)
                } else {
                    val perps = if (dr != 0) listOf(0 to -1, 0 to 1) else listOf(-1 to 0, 1 to 0)
                    for ((pdr, pdc) in perps) {
                        val diagR = nr + pdr; val diagC = nc + pdc
                        if (!inBounds(diagR, diagC)) continue
                        if (!isMovementBlocked(s, Pos(nr, nc), Pos(diagR, diagC))) {
                            moves += Pos(diagR, diagC)
                        }
                    }
                }
            } else {
                moves += Pos(nr, nc)
            }
        }
        return moves
    }

    // ------------------------------------------------------------------
    // Wall conflict detection
    // ------------------------------------------------------------------
    fun wallHasConflict(s: GameState, wr: Int, wc: Int, horizontal: Boolean): Boolean {
        return if (horizontal) {
            s.walls.horizontal.any { it.row == wr && abs(it.col - wc) <= 1 } ||
                s.walls.vertical.any { it.row == wr && it.col == wc }
        } else {
            s.walls.vertical.any { it.col == wc && abs(it.row - wr) <= 1 } ||
                s.walls.horizontal.any { it.row == wr && it.col == wc }
        }
    }

    // ------------------------------------------------------------------
    // BFS path check
    // ------------------------------------------------------------------
    fun hasPath(s: GameState, playerIndex: Int): Boolean {
        val p = s.players[playerIndex]
        val goalRow = if (playerIndex == 0) 0 else BOARD - 1
        val visited = Array(BOARD) { BooleanArray(BOARD) }
        val queue = ArrayDeque<Pos>()
        queue += Pos(p.row, p.col); visited[p.row][p.col] = true

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur.row == goalRow) return true
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nr = cur.row + dr; val nc = cur.col + dc
                if (!inBounds(nr, nc) || visited[nr][nc]) continue
                if (isMovementBlocked(s, cur, Pos(nr, nc))) continue
                visited[nr][nc] = true; queue += Pos(nr, nc)
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // Client-side wall validation (mirrors server checks)
    // ------------------------------------------------------------------
    fun isWallValid(s: GameState, wr: Int, wc: Int, horizontal: Boolean, pi: Int): Boolean {
        if (s.players[pi].wallsLeft <= 0) return false
        if (wr !in 0 until WALL_GRID || wc !in 0 until WALL_GRID) return false
        if (wallHasConflict(s, wr, wc, horizontal)) return false
        val newWalls = if (horizontal)
            s.walls.copy(horizontal = s.walls.horizontal + WallPos(wr, wc))
        else
            s.walls.copy(vertical = s.walls.vertical + WallPos(wr, wc))
        val specState = s.copy(walls = newWalls)
        return hasPath(specState, 0) && hasPath(specState, 1)
    }

    // ------------------------------------------------------------------
    // Local play (single-player): the same rules the server applies in applyAction
    // ------------------------------------------------------------------
    sealed interface Action {
        data class Move(val row: Int, val col: Int) : Action
        data class Wall(val row: Int, val col: Int, val horizontal: Boolean) : Action
    }

    fun initialState(firstTurn: Int = 0) = GameState(
        players = listOf(
            PlayerState(BOARD - 1, BOARD / 2, WALLS_PER_PLAYER), // player 0 starts at the bottom, goal row 0
            PlayerState(0, BOARD / 2, WALLS_PER_PLAYER),         // player 1 starts at the top, goal row 8
        ),
        walls = WallsState(),
        currentTurn = firstTurn,
        status = "playing",
        winner = null,
    )

    fun goalRow(playerIndex: Int) = if (playerIndex == 0) 0 else BOARD - 1

    /** Applies [action] for [pi] and returns the new state, or null if the action is illegal. */
    fun apply(s: GameState, pi: Int, action: Action): GameState? {
        if (s.status != "playing" || s.currentTurn != pi) return null
        return when (action) {
            is Action.Move -> {
                if (getValidMoves(s, pi).none { it.row == action.row && it.col == action.col }) return null
                val won = action.row == goalRow(pi)
                s.copy(
                    players = s.players.mapIndexed { i, p -> if (i == pi) p.copy(row = action.row, col = action.col) else p },
                    currentTurn = 1 - pi,
                    status = if (won) "finished" else "playing",
                    winner = if (won) pi else null,
                )
            }
            is Action.Wall -> {
                if (!isWallValid(s, action.row, action.col, action.horizontal, pi)) return null
                val pos = WallPos(action.row, action.col)
                s.copy(
                    players = s.players.mapIndexed { i, p -> if (i == pi) p.copy(wallsLeft = p.wallsLeft - 1) else p },
                    walls = if (action.horizontal) s.walls.copy(horizontal = s.walls.horizontal + pos)
                    else s.walls.copy(vertical = s.walls.vertical + pos),
                    currentTurn = 1 - pi,
                )
            }
        }
    }

    /** Shortest wall-respecting path (ignoring the other pawn) from the player to their goal row, start included. */
    fun shortestPath(s: GameState, playerIndex: Int): List<Pos>? {
        val p = s.players[playerIndex]
        val goal = goalRow(playerIndex)
        val start = Pos(p.row, p.col)
        val parent = HashMap<Pos, Pos?>()
        parent[start] = null
        val queue = ArrayDeque<Pos>()
        queue += start
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur.row == goal) {
                val path = ArrayList<Pos>()
                var at: Pos? = cur
                while (at != null) { path += at; at = parent[at] }
                return path.reversed()
            }
            for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val next = Pos(cur.row + dr, cur.col + dc)
                if (!inBounds(next.row, next.col) || next in parent) continue
                if (isMovementBlocked(s, cur, next)) continue
                parent[next] = cur
                queue += next
            }
        }
        return null
    }
}
