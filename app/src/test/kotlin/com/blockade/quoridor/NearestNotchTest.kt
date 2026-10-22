package com.blockade.quoridor

import com.blockade.quoridor.ui.game.nearestNotch
import org.junit.Assert.assertEquals
import org.junit.Test

class NearestNotchTest {
    // 100px cells with 20px gutters: stride 120, notch centres at 110, 230, 350, ...
    private val cell = 100f
    private val gap = 20f

    @Test fun `a point on a notch centre snaps to that notch`() {
        assertEquals(0 to 0, nearestNotch(110f, 110f, cell, gap))
        assertEquals(3 to 5, nearestNotch(5 * 120f + 110f, 3 * 120f + 110f, cell, gap))
        assertEquals(7 to 7, nearestNotch(7 * 120f + 110f, 7 * 120f + 110f, cell, gap))
    }

    @Test fun `a point snaps to the nearest notch not the cell it is in`() {
        // Inside the first cell, closer to the notch at 110 than to the board edge.
        assertEquals(0 to 0, nearestNotch(60f, 70f, cell, gap))
        // Just past the halfway point between notch 0 (110) and notch 1 (230) goes to notch 1.
        assertEquals(0 to 1, nearestNotch(171f, 110f, cell, gap))
        assertEquals(0 to 0, nearestNotch(169f, 110f, cell, gap))
    }

    @Test fun `points outside the board clamp to the edge notches`() {
        assertEquals(0 to 0, nearestNotch(-500f, -500f, cell, gap))
        assertEquals(7 to 7, nearestNotch(5000f, 5000f, cell, gap))
        assertEquals(0 to 7, nearestNotch(5000f, -20f, cell, gap))
    }
}
