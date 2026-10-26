package com.blockade.quoridor.domain.rules

import com.blockade.quoridor.domain.model.GameState

/** Two minutes of thinking time per player over the whole online game. */
object GameClock {
    const val GAME_TIME_MS = 120_000L

    fun remaining(state: GameState, seat: Int, updatedAtMs: Long, nowMs: Long): Long {
        val balance = state.remainingMs?.getOrNull(seat) ?: GAME_TIME_MS
        val elapsed = if (state.status == "playing" && state.currentTurn == seat)
            (nowMs - updatedAtMs).coerceAtLeast(0) else 0L
        return (balance - elapsed).coerceAtLeast(0)
    }

    fun settle(state: GameState, updatedAtMs: Long, nowMs: Long): GameState = state.copy(
        remainingMs = (0..1).map { remaining(state, it, updatedAtMs, nowMs) },
    )
}
