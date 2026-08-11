// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.deox9.musicplayer.audio.AudioChain
import com.deox9.musicplayer.audio.AudioChainConfig
import java.nio.ByteBuffer

/**
 * Puts the DSP chain into the player's own signal path.
 *
 * The alternatives it replaces both had hard limits. ReplayGain was applied by
 * setting `player.volume`, which cannot exceed 1.0 — so a track measured as needing
 * a boost simply never got one, and the setting appeared to work while doing nothing
 * in that direction. The equaliser used the platform `AudioEffect`, which offers
 * whatever bands the device happens to implement, at whatever frequencies, with no
 * control over Q; the app was mapping ten virtual sliders onto them and hoping.
 *
 * Running here instead means the same arithmetic on every device, with real filter
 * design, and a limiter that can see the result.
 *
 * Everything in here is called on the audio thread. It allocates on format changes
 * only — never per buffer — because an allocation in this path is a dropout.
 */
@OptIn(UnstableApi::class)
class DeoAudioProcessor : BaseAudioProcessor() {

    private var chain: AudioChain? = null

    @Volatile
    private var pendingConfig: AudioChainConfig = AudioChainConfig()

    /** Latest gain reduction from the limiter, for the signal-chain readout. */
    val limiterReductionDb: Double get() = chain?.limiterReductionDb ?: 0.0

    /**
     * Swaps the whole configuration.
     *
     * Safe from any thread: the value is volatile and picked up by the audio thread
     * on its next buffer. Applying it directly would mean rebuilding filters
     * underneath a call to [queueInput].
     */
    fun setConfig(config: AudioChainConfig) {
        pendingConfig = config
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Both encodings have to be handled. Enabling float *output* on the sink is a
        // preference about what reaches the device; it says nothing about what
        // arrives here, which is whatever the decoder produced — and for most codecs
        // that is 16-bit. Accepting float only made every track fail to play with a
        // MediaCodecAudioRenderer error, because an unhandled format here kills the
        // renderer rather than being skipped.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        chain = AudioChain(inputAudioFormat.sampleRate, inputAudioFormat.channelCount).apply {
            configure(pendingConfig)
        }
        // Output matches input. The processing is done in float internally either
        // way, so the headroom is real regardless; converting the format as well
        // would change what every stage downstream expects.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val chain = chain ?: return
        if (chain.config != pendingConfig) chain.configure(pendingConfig)

        val bytes = inputBuffer.remaining()
        if (bytes == 0) return

        val output = replaceOutputBuffer(bytes)
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val sampleCount = bytes / if (isFloat) BYTES_PER_FLOAT else BYTES_PER_SHORT
        val frameCount = sampleCount / inputAudioFormat.channelCount

        // Copied out, processed, copied back. Working on a FloatArray rather than
        // through the ByteBuffer keeps the inner loops free of per-sample bounds and
        // endianness handling, and gives the chain the headroom to exceed full scale
        // in between stages so the limiter has something to pull back.
        val scratch = scratchFor(sampleCount)
        if (isFloat) {
            inputBuffer.asFloatBuffer().get(scratch, 0, sampleCount)
        } else {
            val shorts = inputBuffer.asShortBuffer()
            for (index in 0 until sampleCount) scratch[index] = shorts.get(index) / SHORT_SCALE
        }
        inputBuffer.position(inputBuffer.limit())

        chain.process(scratch, frameCount)

        if (isFloat) {
            output.asFloatBuffer().put(scratch, 0, sampleCount)
        } else {
            val shorts = output.asShortBuffer()
            for (index in 0 until sampleCount) {
                // Clamped because the limiter can be off, and a value past full scale
                // wraps rather than saturates when it is narrowed.
                val clamped = scratch[index].coerceIn(-1f, MAX_POSITIVE_SAMPLE)
                shorts.put(index, (clamped * SHORT_SCALE).toInt().toShort())
            }
        }
        output.position(output.limit())
        output.flip()
    }

    private var scratch = FloatArray(0)

    private fun scratchFor(floatCount: Int): FloatArray {
        if (scratch.size < floatCount) scratch = FloatArray(floatCount)
        return scratch
    }

    /**
     * Clears the filters' and limiter's history on a seek or track change.
     *
     * Without it, the delay line and the biquad state carry a fragment of the
     * previous position into the new one — audible as a click at the seek point.
     */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        chain?.reset()
    }

    override fun onReset() {
        chain = null
        scratch = FloatArray(0)
    }

    private companion object {
        const val BYTES_PER_FLOAT = 4
        const val BYTES_PER_SHORT = 2
        const val SHORT_SCALE = 32768f

        /** Two's complement is asymmetric: +32768 does not exist. */
        const val MAX_POSITIVE_SAMPLE = 32767f / SHORT_SCALE
    }
}
