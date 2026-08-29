// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The compliance cases from EBU Tech 3341.
 *
 * These are the reason to write the meter rather than approximate it: each case has a
 * published expected value and a stated tolerance, so "it looks about right" is not
 * an available answer. The tolerance for integrated loudness is ±0.1 LU.
 */
class LoudnessMeterTest {

    private val sampleRate = 48_000

    /**
     * A sine whose *peak* is the stated dBFS.
     *
     * Worth stating, because the other reading — dBFS as RMS — is equally plausible
     * and puts every expected value out by 3 dB. That the -23 dBFS case lands on
     * -23.0 LUFS is what confirms this is the one EBU Tech 3341 means.
     */
    private fun sine(
        levelDbfs: Double,
        seconds: Double,
        frequencyHz: Double = 1000.0,
        channels: Int = 2,
        rate: Int = sampleRate,
    ): FloatArray {
        val amplitude = 10.0.pow(levelDbfs / 20.0)
        val frames = (rate * seconds).toInt()
        val out = FloatArray(frames * channels)
        var index = 0
        for (frame in 0 until frames) {
            val value = (amplitude * sin(2.0 * PI * frequencyHz * frame / rate)).toFloat()
            repeat(channels) { out[index++] = value }
        }
        return out
    }

    private fun measure(
        samples: FloatArray,
        channels: Int = 2,
        rate: Int = sampleRate,
    ): LoudnessResult {
        val meter = LoudnessMeter(rate, channels)
        meter.add(samples, samples.size / channels)
        return meter.result()
    }

    /** EBU Tech 3341 case 1: stereo 1 kHz sine at -23 dBFS reads -23.0 LUFS. */
    @Test
    fun `a 1 kHz sine at minus 23 dBFS measures minus 23 LUFS`() {
        val result = measure(sine(levelDbfs = -23.0, seconds = 20.0))

        assertNotNull(result.integratedLufs)
        assertEquals(-23.0, result.integratedLufs!!, TOLERANCE_LU)
    }

    /** EBU Tech 3341 case 2: the same at -33 dBFS reads -33.0 LUFS. */
    @Test
    fun `a 1 kHz sine at minus 33 dBFS measures minus 33 LUFS`() {
        val result = measure(sine(levelDbfs = -33.0, seconds = 20.0))

        assertEquals(-33.0, result.integratedLufs!!, TOLERANCE_LU)
    }

    /**
     * The measurement must not depend on the file's sample rate.
     *
     * This is what the per-rate filter design buys: using the standard's 48 kHz
     * coefficients on 44.1 kHz material shifts both filters by about 9%.
     */
    @Test
    fun `the same signal measures the same at 44_1 kHz`() {
        val result = measure(
            sine(levelDbfs = -23.0, seconds = 20.0, rate = 44_100),
            rate = 44_100,
        )

        assertEquals(-23.0, result.integratedLufs!!, TOLERANCE_LU)
    }

    @Test
    fun `mono measures the same as dual mono`() {
        val mono = measure(sine(levelDbfs = -23.0, seconds = 20.0, channels = 1), channels = 1)
        val stereo = measure(sine(levelDbfs = -23.0, seconds = 20.0, channels = 2))

        // Two identical channels carry twice the power of one, which is +3 LU.
        assertEquals(stereo.integratedLufs!! - 3.0103, mono.integratedLufs!!, TOLERANCE_LU)
    }

    /**
     * The relative gate is the whole point of gated loudness: a track that is loud
     * for ten seconds and near-silent for fifty should measure close to the loud
     * part, because that is how loud it sounds.
     */
    @Test
    fun `quiet passages are gated out rather than dragging the mean down`() {
        val loud = sine(levelDbfs = -23.0, seconds = 10.0)
        val quiet = sine(levelDbfs = -60.0, seconds = 50.0)

        val result = measure(loud + quiet)

        assertEquals(-23.0, result.integratedLufs!!, GATED_TOLERANCE_LU)
    }

    /** Absolute gate: nothing above -70 LUFS means nothing to report. */
    @Test
    fun `silence has no measurement rather than a very small one`() {
        val result = measure(FloatArray(sampleRate * 2 * 5))

        assertNull(result.integratedLufs)
        assertEquals(0, result.gatedBlockCount)
    }

    /** Shorter than one 400 ms block: no complete block, so nothing to integrate. */
    @Test
    fun `a clip too short to fill a block has no measurement`() {
        val result = measure(sine(levelDbfs = -23.0, seconds = 0.2))

        assertNull(result.integratedLufs)
    }

    @Test
    fun `sample peak is the largest magnitude seen, including above full scale`() {
        val samples = floatArrayOf(0.5f, -0.9f, 1.4f, 0.1f)

        val result = measure(samples)

        assertEquals(1.4, result.samplePeak, 1e-6)
    }

    @Test
    fun `a rate or channel count that cannot work is rejected at construction`() {
        listOf(
            { LoudnessMeter(sampleRate = 0, channelCount = 2) },
            { LoudnessMeter(sampleRate = -48_000, channelCount = 2) },
            { LoudnessMeter(sampleRate = 48_000, channelCount = 0) },
            { LoudnessMeter(sampleRate = 48_000, channelCount = 9) },
        ).forEach { construct ->
            runCatching { construct() }.fold(
                onSuccess = { throw AssertionError("expected rejection") },
                onFailure = { assertTrue(it is IllegalArgumentException) },
            )
        }
    }

    @Test
    fun `LFE is excluded and surrounds are weighted above the fronts`() {
        assertEquals(1.0, LoudnessMeter.channelWeight(0, 6), 0.0)
        assertEquals(1.0, LoudnessMeter.channelWeight(2, 6), 0.0)
        assertEquals(0.0, LoudnessMeter.channelWeight(3, 6), 0.0)
        assertEquals(1.41, LoudnessMeter.channelWeight(4, 6), 0.0)
        assertEquals(1.41, LoudnessMeter.channelWeight(5, 6), 0.0)
        // Stereo has no surrounds to weight, whatever the index.
        assertEquals(1.0, LoudnessMeter.channelWeight(1, 2), 0.0)
    }

    private companion object {
        /** EBU Tech 3341's stated tolerance for integrated loudness. */
        const val TOLERANCE_LU = 0.1

        /**
         * Looser for the gating case: the boundary blocks straddling the transition
         * are genuinely part-loud and part-quiet, so the exact figure depends on
         * where the 400ms windows happen to land.
         */
        const val GATED_TOLERANCE_LU = 0.5
    }
}
