// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class AudioChainTest {

    private val sampleRate = 48_000
    private val channels = 2

    private fun sine(amplitude: Double, seconds: Double, frequencyHz: Double = 1000.0): FloatArray {
        val frames = (sampleRate * seconds).toInt()
        val out = FloatArray(frames * channels)
        var index = 0
        for (frame in 0 until frames) {
            val value = (amplitude * sin(2.0 * PI * frequencyHz * frame / sampleRate)).toFloat()
            repeat(channels) { out[index++] = value }
        }
        return out
    }

    /** Peak of the back half, so the limiter's attack and any filter transient are past. */
    private fun settledPeak(samples: FloatArray): Double {
        var peak = 0.0
        for (index in samples.size / 2 until samples.size) {
            peak = maxOf(peak, abs(samples[index].toDouble()))
        }
        return peak
    }

    // ---- equaliser ----------------------------------------------------------

    @Test
    fun `a peaking band changes the level at its frequency by its gain`() {
        val eq = ParametricEq(sampleRate, channels)
        eq.setBands(listOf(EqBand(EqBandType.Peaking, frequencyHz = 1000.0, gainDb = 6.0)))

        val samples = sine(amplitude = 0.1, seconds = 0.5)
        eq.process(samples, samples.size / channels)

        val gainDb = 20.0 * log10(settledPeak(samples) / 0.1)
        assertEquals(6.0, gainDb, 0.1)
    }

    @Test
    fun `a band away from the signal leaves it alone`() {
        val eq = ParametricEq(sampleRate, channels)
        eq.setBands(listOf(EqBand(EqBandType.Peaking, frequencyHz = 60.0, gainDb = 12.0, q = 4.0)))

        val samples = sine(amplitude = 0.1, seconds = 0.5)
        eq.process(samples, samples.size / channels)

        assertEquals(0.1, settledPeak(samples), 0.005)
    }

    /** Ten flat bands must cost nothing, because that is the default state. */
    @Test
    fun `bands with no gain are dropped rather than run at unity`() {
        val eq = ParametricEq(sampleRate, channels)
        eq.setBands(EqBand.fromGraphicLevels(List(10) { 0 }))

        assertTrue(eq.isTransparent)
    }

    @Test
    fun `a high-pass band is kept even at zero gain because gain is not what it does`() {
        val eq = ParametricEq(sampleRate, channels)
        eq.setBands(listOf(EqBand(EqBandType.HighPass, frequencyHz = 80.0, gainDb = 0.0)))

        assertFalse(eq.isTransparent)
    }

    @Test
    fun `the old graphic levels convert to bands at the same frequencies`() {
        val bands = EqBand.fromGraphicLevels(listOf(600, 0, 0, 0, 0, 0, 0, 0, 0, -300))

        assertEquals(10, bands.size)
        assertEquals(31.0, bands.first().frequencyHz, 0.0)
        assertEquals(6.0, bands.first().gainDb, 1e-9)
        assertEquals(16000.0, bands.last().frequencyHz, 0.0)
        assertEquals(-3.0, bands.last().gainDb, 1e-9)
    }

    /** Channels must filter independently or the equaliser smears the stereo image. */
    @Test
    fun `each channel has its own filter state`() {
        val eq = ParametricEq(sampleRate, channels)
        eq.setBands(listOf(EqBand(EqBandType.LowPass, frequencyHz = 500.0)))

        // Left carries the signal, right is silent.
        val samples = FloatArray(1000 * channels)
        for (frame in 0 until 1000) samples[frame * channels] = 1.0f

        eq.process(samples, 1000)

        for (frame in 0 until 1000) {
            assertEquals("right channel at frame $frame", 0.0f, samples[frame * channels + 1], 0.0f)
        }
    }

    // ---- limiter ------------------------------------------------------------

    @Test
    fun `a signal below the threshold passes at its own level`() {
        val limiter = SoftLimiter(sampleRate, channels)
        val samples = sine(amplitude = 0.2, seconds = 0.5)

        limiter.process(samples, samples.size / channels)

        assertEquals(0.2, settledPeak(samples), 0.005)
    }

    /** The point of the thing: nothing escapes above the threshold. */
    @Test
    fun `a signal above the threshold is brought down to it`() {
        val limiter = SoftLimiter(sampleRate, channels)
        val samples = sine(amplitude = 2.0, seconds = 0.5)

        limiter.process(samples, samples.size / channels)

        val thresholdLinear = 10.0.pow(SoftLimiter.DEFAULT_THRESHOLD_DB / 20.0)
        assertTrue(
            "peak ${settledPeak(samples)} exceeded threshold $thresholdLinear",
            settledPeak(samples) <= thresholdLinear * TOLERANCE_RATIO,
        )
    }

    /**
     * The reason for look-ahead. A transient arriving with no warning is exactly the
     * case a limiter without it lets through, because its attack cannot begin until
     * the peak has already been output.
     */
    @Test
    fun `a sudden transient does not escape before the attack starts`() {
        val limiter = SoftLimiter(sampleRate, channels)
        val samples = FloatArray(sampleRate * channels)
        // A full second of quiet, then one very loud frame.
        val spikeFrame = sampleRate / 2
        samples[spikeFrame * channels] = 4.0f
        samples[spikeFrame * channels + 1] = 4.0f

        limiter.process(samples, sampleRate)

        var peak = 0.0
        for (sample in samples) peak = maxOf(peak, abs(sample.toDouble()))
        val thresholdLinear = 10.0.pow(SoftLimiter.DEFAULT_THRESHOLD_DB / 20.0)
        assertTrue("transient peaked at $peak", peak <= thresholdLinear * TOLERANCE_RATIO)
    }

    @Test
    fun `gain returns to unity after the loud part has passed`() {
        val limiter = SoftLimiter(sampleRate, channels)
        val loud = sine(amplitude = 2.0, seconds = 0.2)
        val quiet = sine(amplitude = 0.2, seconds = 1.0)

        limiter.process(loud, loud.size / channels)
        limiter.process(quiet, quiet.size / channels)

        assertEquals(0.2, settledPeak(quiet), 0.005)
    }

    @Test
    fun `the reduction it applied is reported for the signal chain readout`() {
        val limiter = SoftLimiter(sampleRate, channels)
        limiter.process(sine(amplitude = 2.0, seconds = 0.3), (sampleRate * 0.3).toInt())

        assertTrue("expected reduction, got ${limiter.maxGainReductionDb}", limiter.maxGainReductionDb < -5.0)
    }

    // ---- the whole chain ----------------------------------------------------

    @Test
    fun `gain is applied before the limiter catches what it produced`() {
        val chain = AudioChain(sampleRate, channels)
        chain.configure(AudioChainConfig(gainDb = 12.0, limiterEnabled = true))

        val samples = sine(amplitude = 0.5, seconds = 0.5)
        chain.process(samples, samples.size / channels)

        // +12 dB on 0.5 is 2.0, which the limiter has to pull back under threshold.
        val thresholdLinear = 10.0.pow(SoftLimiter.DEFAULT_THRESHOLD_DB / 20.0)
        assertTrue(settledPeak(samples) <= thresholdLinear * TOLERANCE_RATIO)
    }

    /**
     * The boost that the old `player.volume` path could not do at all: it clamped the
     * multiplier to 1.0, so a track wanting +3 dB simply did not get it.
     */
    @Test
    fun `a positive gain actually raises the level`() {
        val chain = AudioChain(sampleRate, channels)
        chain.configure(AudioChainConfig(gainDb = 6.0, limiterEnabled = false))

        val samples = sine(amplitude = 0.1, seconds = 0.2)
        chain.process(samples, samples.size / channels)

        assertEquals(0.1 * 10.0.pow(6.0 / 20.0), settledPeak(samples), 0.005)
    }

    @Test
    fun `a chain doing nothing says so, so buffers can be passed through`() {
        val chain = AudioChain(sampleRate, channels)

        chain.configure(AudioChainConfig(gainDb = 0.0, bands = emptyList(), limiterEnabled = false))
        assertTrue(chain.isTransparent)

        chain.configure(AudioChainConfig(gainDb = 0.0, bands = emptyList(), limiterEnabled = true))
        assertFalse("the limiter is a stage", chain.isTransparent)

        chain.configure(AudioChainConfig(gainDb = 3.0, limiterEnabled = false))
        assertFalse("gain is a stage", chain.isTransparent)
    }

    @Test
    fun `configuration is replaced whole rather than a piece at a time`() {
        val chain = AudioChain(sampleRate, channels)
        val config = AudioChainConfig(
            gainDb = -4.0,
            bands = listOf(EqBand(EqBandType.Peaking, 800.0, 2.0)),
            limiterEnabled = true,
        )

        chain.configure(config)

        assertEquals(config, chain.config)
    }

    private companion object {
        /** Half a percent, to allow for the knee and one-pole envelope settling. */
        const val TOLERANCE_RATIO = 1.005
    }
}
