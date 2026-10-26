package com.blockade.quoridor

import com.blockade.quoridor.domain.rules.GameClock
import com.blockade.quoridor.domain.rules.QuoridorRules
import org.junit.Assert.assertEquals
import org.junit.Test

class GameClockTest {
    @Test fun cumulativeBalancesSurviveTurnsAndOnlyActiveClockRuns() {
        val start = QuoridorRules.initialState(0)
        val first = GameClock.settle(start, 1000, 31000)
        assertEquals(listOf(90000L, 120000L), first.remainingMs)
        val next = QuoridorRules.apply(first, 0, QuoridorRules.Action.Move(7, 4))!!
        val second = GameClock.settle(next, 31000, 51000)
        assertEquals(listOf(90000L, 100000L), second.remainingMs)
        val wall = QuoridorRules.apply(second, 1, QuoridorRules.Action.Wall(3, 3, true))!!
        assertEquals(80000L, GameClock.remaining(wall, 0, 51000, 61000))
        assertEquals(100000L, GameClock.remaining(wall, 1, 51000, 61000))
    }

    @Test fun clocksClampAtZeroAndFreezeWhenFinished() {
        val state = QuoridorRules.initialState(1).copy(remainingMs = listOf(50000L, 1500L))
        assertEquals(1L, GameClock.remaining(state, 1, 1000, 2499))
        assertEquals(0L, GameClock.remaining(state, 1, 1000, 2500))
        assertEquals(0L, GameClock.remaining(state, 1, 1000, 900000))
        assertEquals(1500L, GameClock.remaining(state.copy(status = "finished"), 1, 1000, 900000))
        assertEquals(1500L, GameClock.remaining(state, 1, 1000, 0))
    }
}
