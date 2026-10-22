package com.blockade.quoridor.ui.sound

import android.media.AudioManager
import android.media.ToneGenerator
import javax.inject.Inject
import javax.inject.Singleton

enum class Sfx(val tone: Int, val durationMs: Int) {
    MOVE(ToneGenerator.TONE_PROP_BEEP, 80),
    WALL(ToneGenerator.TONE_PROP_BEEP2, 120),
    INVALID(ToneGenerator.TONE_SUP_ERROR, 150),
    WIN(ToneGenerator.TONE_PROP_ACK, 400),
    LOSE(ToneGenerator.TONE_PROP_NACK, 400),
}

/** Plays short built-in tones; no audio assets needed. Failures (no audio device, etc.) are ignored. */
@Singleton
class SoundPlayer @Inject constructor() {
    private val generator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
    }

    fun play(sfx: Sfx) {
        runCatching { generator?.startTone(sfx.tone, sfx.durationMs) }
    }
}
