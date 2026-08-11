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
 * Gain, then equaliser, then limiter.
 *
 * The order is the whole design. Gain first because ReplayGain is a property of the
 * recording, not of the listener's taste. The equaliser next, so its bands work on a
 * signal already at the right level. The limiter last, because it is the only thing
 * that can see what the other two added together produced — put it anywhere else and
 * the stage after it can push the signal back over.
 */
class AudioChain(
    private val sampleRate: Int,
    private val channelCount: Int,
) {
    private val eq = ParametricEq(sampleRate, channelCount)
    private val limiter = SoftLimiter(sampleRate, channelCount)

    private var linearGain = 1.0f
    private var limiterEnabled = true

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
        get() = linearGain == 1.0f && eq.isTransparent && !limiterEnabled

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
    }
}
