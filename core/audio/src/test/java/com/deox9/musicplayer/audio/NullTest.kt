// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * The null test: does the player change audio it was not asked to change?
 *
 * The standard check in audio software and almost unheard of in an Android player.
 * Known samples go in, the output is subtracted from the input, and anything left
 * over is something the signal path did without being told to. A player that fails
 * this is colouring every track before any setting has been touched.
 *
 * This is the software half, which is the half that can be exact: the arithmetic is
 * deterministic, so the residual should be precisely zero rather than merely small.
 * It does not cover what the device does after the sink — that needs a loopback
 * capture, and is a separate exercise.
 */
class NullTest {

    private val sampleRate = 48_000
    private val channels = 2

    /**
     * Content with something for every stage to get wrong: a tone, a quiet passage,
     * a near-full-scale passage, and hard transients.
     */
    private fun testSignal(): FloatArray {
        val frames = sampleRate / 2
        val out = FloatArray(frames * channels)
        var index = 0
        for (frame in 0 until frames) {
            val t = frame.toDouble() / sampleRate
            val tone = 0.5 * sin(2.0 * PI * 997.0 * t)
            val quiet = 0.001 * sin(2.0 * PI * 3_000.0 * t)
            val transient = if (frame % 4_000 == 0) 0.8 else 0.0
            val value = (tone + quiet + transient).coerceIn(-1.0, 1.0).toFloat()
            out[index++] = value
            // Channels deliberately differ, so a stage that crosses them shows up.
            out[index++] = (value * 0.75f)
        }
        return out
    }

    /**
     * Peak of input minus output, in dBFS, with the output shifted back by
     * [alignFrames] first.
     *
     * The alignment is not a fudge. A look-ahead limiter delays the signal by its
     * look-ahead whether or not it reduces anything, so subtracting sample-for-sample
     * measures that delay rather than any change to the audio — on a 997 Hz tone that
     * alone produces a residual near full scale and says nothing about fidelity.
     */
    private fun residualDb(original: FloatArray, processed: FloatArray, alignFrames: Int = 0): Double {
        var peak = 0.0
        val offset = alignFrames * channels
        for (index in offset until original.size) {
            val difference = original[index - offset] - processed[index]
            peak = maxOf(peak, abs(difference).toDouble())
        }
        return if (peak == 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(peak)
    }

    /** What the limiter's look-ahead costs in frames, at this rate. */
    private val lookAheadFrames =
        ((SoftLimiter.DEFAULT_ATTACK_MS / 1_000.0) * sampleRate).toInt().coerceAtLeast(1)

    /** Content that stays well clear of the limiter threshold, so it never engages. */
    private fun quietSignal(): FloatArray {
        val frames = sampleRate / 2
        val out = FloatArray(frames * channels)
        var index = 0
        for (frame in 0 until frames) {
            val t = frame.toDouble() / sampleRate
            val value = (0.25 * sin(2.0 * PI * 997.0 * t)).toFloat()
            out[index++] = value
            out[index++] = value * 0.5f
        }
        return out
    }

    private fun chainWith(config: AudioChainConfig, signal: FloatArray = testSignal()): FloatArray {
        val chain = AudioChain(sampleRate, channels)
        chain.configure(config)
        val buffer = signal.copyOf()
        // In realistic buffer-sized pieces, because processing the lot in one call
        // would hide any state carried wrongly across a boundary.
        val blockFrames = 1_024
        var frame = 0
        val total = buffer.size / channels
        while (frame < total) {
            val count = minOf(blockFrames, total - frame)
            val block = buffer.copyOfRange(frame * channels, (frame + count) * channels)
            chain.process(block, count)
            block.copyInto(buffer, frame * channels)
            frame += count
        }
        return buffer
    }

    /**
     * The headline result. Everything off means everything off — not "close enough".
     */
    @Test
    fun `a chain with every stage neutral returns the signal untouched`() {
        val original = testSignal()

        val processed = chainWith(
            AudioChainConfig(gainDb = 0.0, bands = emptyList(), limiterEnabled = false),
        )

        assertEquals(
            "residual ${residualDb(original, processed)} dBFS",
            Double.NEGATIVE_INFINITY,
            residualDb(original, processed),
            0.0,
        )
    }

    /**
     * The configuration the app actually ships with, on content that never asks the
     * limiter to do anything.
     *
     * Aligned by the look-ahead first, because the limiter delays the signal by that
     * much unconditionally. What this asserts is the meaningful claim: below the
     * threshold the limiter changes the audio in no way other than moving it later.
     */
    @Test
    fun `the default chain is transparent below the threshold apart from its delay`() {
        val original = quietSignal()

        val processed = chainWith(AudioChainConfig(), signal = original)

        assertEquals(
            "residual ${residualDb(original, processed, lookAheadFrames)} dBFS",
            Double.NEGATIVE_INFINITY,
            residualDb(original, processed, lookAheadFrames),
            0.0,
        )
    }

    /**
     * The delay itself, stated rather than left implicit.
     *
     * 1.5 ms is inaudible on its own and is the price of catching a transient before
     * it clips rather than after. It is recorded here because it is real latency on
     * every track the default settings play, and because it is the same delay line a
     * flush empties at a gapless join.
     */
    @Test
    fun `the limiter delays the signal by its look-ahead`() {
        val original = quietSignal()

        val processed = chainWith(AudioChainConfig(), signal = original)

        // Unaligned, the same audio nulls to nothing like a signal against a
        // different one; aligned, it nulls perfectly. That gap is the delay.
        assertTrue(
            "unaligned residual was ${residualDb(original, processed)} dBFS",
            residualDb(original, processed) > -60.0,
        )
        assertEquals(72, lookAheadFrames)
    }

    /** And it does act when it should, or it would be transparent for the wrong reason. */
    @Test
    fun `the limiter is not transparent to content above the threshold`() {
        val original = testSignal()

        val processed = chainWith(AudioChainConfig(), signal = original)

        assertTrue(
            "residual ${residualDb(original, processed, lookAheadFrames)} dBFS",
            residualDb(original, processed, lookAheadFrames) > -60.0,
        )
    }

    /** Bands that are set to do nothing must cost nothing, not merely little. */
    @Test
    fun `transparent equaliser bands do not alter the signal`() {
        val original = testSignal()

        val processed = chainWith(
            AudioChainConfig(
                bands = EqBand.fromGraphicLevels(List(10) { 0 }),
                limiterEnabled = false,
            ),
        )

        assertEquals(
            "residual ${residualDb(original, processed)} dBFS",
            Double.NEGATIVE_INFINITY,
            residualDb(original, processed),
            0.0,
        )
    }

    /** A disabled band is not applied even when its own values are not neutral. */
    @Test
    fun `a disabled band is not applied`() {
        val original = testSignal()

        val processed = chainWith(
            AudioChainConfig(
                bands = listOf(
                    EqBand(EqBandType.Peaking, 1_000.0, gainDb = 12.0, enabled = false),
                ),
                limiterEnabled = false,
            ),
        )

        assertEquals(
            "residual ${residualDb(original, processed)} dBFS",
            Double.NEGATIVE_INFINITY,
            residualDb(original, processed),
            0.0,
        )
    }

    /**
     * The 16-bit round trip the sink performs on every buffer.
     *
     * The DSP works in float, so every sample of a 16-bit track is divided out to
     * float and multiplied back on the way to the sink. Both factors are powers of
     * two and 16 bits fit inside a float's mantissa, so this should be exact for
     * every representable sample — but "should be" is what a null test is for, and
     * an off-by-one here would be a quantisation error on every track ever played.
     */
    @Test
    fun `the sixteen-bit round trip is exact for every sample value`() {
        val scale = 32_768f
        val maxPositive = 32_767f / scale

        for (value in Short.MIN_VALUE..Short.MAX_VALUE) {
            val asFloat = value / scale
            val clamped = asFloat.coerceIn(-1f, maxPositive)
            val back = (clamped * scale).toInt().toShort()

            assertEquals("sample $value did not survive the round trip", value.toShort(), back)
        }
    }

    /**
     * Which is not to say the chain never acts. A test that only proves things stay
     * the same would pass just as happily against a chain that did nothing at all.
     */
    @Test
    fun `the null test can fail, so a chain that does something shows up`() {
        val original = testSignal()

        val processed = chainWith(AudioChainConfig(gainDb = -0.1, limiterEnabled = false))

        assertTrue(
            "a 0.1 dB cut left no residual, so this test proves nothing",
            residualDb(original, processed) > -120.0,
        )
    }
}
