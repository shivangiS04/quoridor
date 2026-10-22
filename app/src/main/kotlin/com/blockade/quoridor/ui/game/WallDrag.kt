package com.blockade.quoridor.ui.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * A wall being carried from the tray onto the board. The tray drives it (start/move/end, in root coordinates);
 * the board reads it to show where the wall would land and to place it on release.
 */
@Stable
class WallDragState {
    var active by mutableStateOf(false)
        private set
    var horizontal by mutableStateOf(true)
        private set

    /** Fingertip position in root coordinates. */
    var finger by mutableStateOf(Offset.Zero)
        private set

    /** Set by the board: is the notch under the held wall a legal placement? Null when it is not over the board. */
    var targetValid by mutableStateOf<Boolean?>(null)

    /** Set by the board: its side length in px, so the held piece can be drawn at board scale anywhere on screen. */
    var boardSidePx by mutableFloatStateOf(0f)

    /** Set by the board: called when the finger is lifted, to place (or reject) the wall under it. */
    var onRelease: (() -> Unit)? = null

    fun start(horizontal: Boolean, at: Offset) {
        this.horizontal = horizontal
        finger = at
        active = true
    }

    fun move(to: Offset) {
        finger = to
    }

    fun end() {
        if (active) onRelease?.invoke()
        cancel()
    }

    fun cancel() {
        active = false
        targetValid = null
    }
}
