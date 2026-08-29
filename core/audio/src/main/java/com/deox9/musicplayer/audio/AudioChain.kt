// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

/**
 * What the DSP is currently set to do.
 *
 * A value rather than a pile of setters, so the whole configuration changes at once.
 * Applied piecemeal, a user dragging an equaliser band could be heard against the old
 * gain for a buffer or two.
 */
data class AudioChainConfig(
    val gainDb: Double = 0.0,
    val bands: List<EqBand> = emptyList(),
    val limiterEnabled: Boolean = true,
) {
    /** True when the chain would return the signal unchanged, and can be skipped. */
    val isTransparent: Boolean
        get() = gainDb == 0.0 && bands.all { it.isTransparent } && !limiterEnabled
}

/**
 * Gain, then equaliser, then limiter, then the fade.
 *
 * The order is the whole design. Gain first because ReplayGain is a property of the
 * recording, not of the listener's taste. The equaliser next, so its bands work on a
 * signal already at the right level. The limiter after those, because it is the only
 * thing that can see what the two of them added together produced — put it anywhere
 * else and the stage after it can push the signal back over.
 *
 * The fade is last, and outside all of it. It is not part of processing the
 * recording; it is the listener leaving. Running it before the limiter would let the
 * limiter respond to the fade, quietly working against it.
 */
class AudioChain(
    private val sampleRate: Int,
    private val channelCount: Int,
) {
    private val eq = ParametricEq(sampleRate, channelCount)
    private val limiter = SoftLimiter(sampleRate, channelCount)

    private var linearGain = 1.0f
    private var limiterEnabled = true

    private var fade: FadeRamp? = null
    private var fadeFrame = 0

    var config: AudioChainConfig = AudioChainConfig()
        private set

    init {
        configure(AudioChainConfig())
    }

    fun configure(value: AudioChainConfig) {
        config = value
        linearGain = ReplayGain.dbToLinear(value.gainDb).toFloat()
        limiterEnabled = value.limiterEnabled
        eq.setBands(value.bands)
    }

    /**
     * True when every stage is a no-op, so the caller can pass buffers through
     * untouched instead of copying them for nothing.
     */
    val isTransparent: Boolean
        get() = linearGain == 1.0f && eq.isTransparent && !limiterEnabled && fade == null

    /**
     * Begins a fade, replacing any fade already running.
     *
     * Replacing rather than queueing is deliberate: a listener hammering the skip
     * button should get the newest fade from wherever the level currently is, not a
     * backlog of fades to sit through.
     */
    fun startFade(curve: CrossfadeCurve, direction: FadeDirection, durationMs: Int) {
        fade = FadeRamp.of(curve, direction, durationMs, sampleRate)
        fadeFrame = 0
    }

    /** Abandons any fade and returns to full level immediately. */
    fun clearFade() {
        fade = null
        fadeFrame = 0
    }

    /** True while a fade is running or is holding the signal down after one. */
    val isFading: Boolean get() = fade != null

    /**
     * Clears the filters' history without touching the fade.
     *
     * The fade deliberately survives, because a flush is exactly what happens in the
     * middle of one: fading out and then skipping flushes the chain between the two
     * halves, and clearing here would snap the signal back to full level for the
     * moment before the fade-in starts — the click this exists to prevent.
     */
    fun reset() {
        eq.reset()
        limiter.reset()
    }

    /** Largest reduction the limiter has applied, for the signal-chain readout. */
    val limiterReductionDb: Double get() = limiter.maxGainReductionDb

    fun process(interleaved: FloatArray, frameCount: Int) {
        if (linearGain != 1.0f) {
            val sampleCount = frameCount * channelCount
            for (index in 0 until sampleCount) {
                interleaved[index] = interleaved[index] * linearGain
            }
        }

        eq.process(interleaved, frameCount)

        if (limiterEnabled) limiter.process(interleaved, frameCount)

        applyFade(interleaved, frameCount)
    }

    /**
     * Rides the fade across the buffer, one frame at a time.
     *
     * Per frame rather than per buffer because a buffer is several milliseconds
     * long: stepping the gain once per buffer is the zipper noise that makes a fade
     * sound like a staircase.
     *
     * A fade-out that has finished is held rather than cleared, so the signal stays
     * silent until something asks for a fade back in. Clearing it would return the
     * level to full during the silence, which is audible the moment the next track's
     * samples arrive.
     */
    private fun applyFade(interleaved: FloatArray, frameCount: Int) {
        val ramp = fade ?: return

        for (frame in 0 until frameCount) {
            val gain = ramp.gainAt(fadeFrame + frame)
            val base = frame * channelCount
            for (channel in 0 until channelCount) {
                interleaved[base + channel] = interleaved[base + channel] * gain
            }
        }
        fadeFrame += frameCount

        // A completed fade-in has nothing left to say and is dropped, which puts the
        // chain back to unity gain with no per-frame work. A completed fade-out has
        // to stay, holding silence.
        if (ramp.direction == FadeDirection.In && ramp.isComplete(fadeFrame)) {
            clearFade()
        }
    }
}
