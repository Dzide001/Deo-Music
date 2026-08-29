// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class BiquadTest {

    private val sampleRate = 48_000

    private fun magnitudeDb(coefficients: BiquadCoefficients, frequencyHz: Double) =
        20.0 * log10(coefficients.magnitudeAt(frequencyHz, sampleRate))

    /**
     * The published coefficients for stage 1 at 48 kHz, from ITU-R BS.1770-4.
     *
     * The point of designing the filter from its analogue parameters instead of
     * hard-coding these is that it then works at any sample rate — but it has to
     * reproduce the table at the one rate the table covers, or the derivation is
     * wrong in a way nothing else would catch.
     */
    @Test
    fun `K-weighting stage 1 reproduces the published 48 kHz coefficients`() {
        val actual = KWeighting.stage1(sampleRate)

        assertEquals(1.53512485958697, actual.b0, COEFFICIENT_TOLERANCE)
        assertEquals(-2.69169618940638, actual.b1, COEFFICIENT_TOLERANCE)
        assertEquals(1.19839281085285, actual.b2, COEFFICIENT_TOLERANCE)
        assertEquals(-1.69065929318241, actual.a1, COEFFICIENT_TOLERANCE)
        assertEquals(0.73248077421585, actual.a2, COEFFICIENT_TOLERANCE)
    }

    @Test
    fun `K-weighting stage 2 reproduces the published 48 kHz coefficients`() {
        val actual = KWeighting.stage2(sampleRate)

        assertEquals(1.0, actual.b0, COEFFICIENT_TOLERANCE)
        assertEquals(-2.0, actual.b1, COEFFICIENT_TOLERANCE)
        assertEquals(1.0, actual.b2, COEFFICIENT_TOLERANCE)
        assertEquals(-1.99004745483398, actual.a1, COEFFICIENT_TOLERANCE)
        assertEquals(0.99007225036621, actual.a2, COEFFICIENT_TOLERANCE)
    }

    /**
     * The +0.69 dB at 1 kHz is exactly what the meter's -0.691 offset cancels. If
     * these two ever drift apart, every measurement is biased by the difference.
     */
    @Test
    fun `K-weighting has the gain at 1 kHz that the loudness offset assumes`() {
        val shelf = KWeighting.stage1(sampleRate)
        val highPass = KWeighting.stage2(sampleRate)

        val combined = magnitudeDb(shelf, 1000.0) + magnitudeDb(highPass, 1000.0)

        assertEquals(0.691, combined, 0.01)
    }

    @Test
    fun `a peaking filter has its stated gain at its centre frequency`() {
        listOf(-12.0, -3.0, 3.0, 12.0).forEach { gainDb ->
            val filter = BiquadDesign.peaking(1000.0, gainDb, q = 1.0, sampleRate = sampleRate)

            assertEquals(gainDb, magnitudeDb(filter, 1000.0), 0.01)
        }
    }

    /**
     * A band set to 0 dB has to be transparent, not merely close. Users sweep bands
     * back to flat and expect the signal they started with.
     */
    @Test
    fun `a peaking filter at 0 dB is transparent`() {
        val filter = BiquadDesign.peaking(1000.0, gainDb = 0.0, q = 1.0, sampleRate = sampleRate)

        listOf(20.0, 100.0, 1000.0, 5000.0, 15000.0).forEach { frequency ->
            assertEquals(0.0, magnitudeDb(filter, frequency), 1e-9)
        }
    }

    /** A narrow band must not move its neighbours; that is what Q is for. */
    @Test
    fun `a high Q peaking filter leaves nearby frequencies alone`() {
        val filter = BiquadDesign.peaking(1000.0, gainDb = 12.0, q = 8.0, sampleRate = sampleRate)

        assertEquals(12.0, magnitudeDb(filter, 1000.0), 0.01)
        assertTrue(abs(magnitudeDb(filter, 250.0)) < 1.0)
        assertTrue(abs(magnitudeDb(filter, 4000.0)) < 1.0)
    }

    @Test
    fun `shelves reach their gain in the band they act on and unity in the other`() {
        val low = BiquadDesign.lowShelf(200.0, gainDb = 6.0, slope = 1.0, sampleRate = sampleRate)
        assertEquals(6.0, magnitudeDb(low, 20.0), 0.2)
        assertEquals(0.0, magnitudeDb(low, 10_000.0), 0.2)

        val high = BiquadDesign.highShelf(4000.0, gainDb = 6.0, slope = 1.0, sampleRate = sampleRate)
        assertEquals(6.0, magnitudeDb(high, 18_000.0), 0.2)
        assertEquals(0.0, magnitudeDb(high, 100.0), 0.2)
    }

    /** A Butterworth-Q pass filter is -3 dB at its corner, by definition. */
    @Test
    fun `pass filters are 3 dB down at the corner frequency`() {
        val butterworthQ = 0.7071067811865476

        val highPass = BiquadDesign.highPass(1000.0, butterworthQ, sampleRate)
        assertEquals(-3.0103, magnitudeDb(highPass, 1000.0), 0.05)

        val lowPass = BiquadDesign.lowPass(1000.0, butterworthQ, sampleRate)
        assertEquals(-3.0103, magnitudeDb(lowPass, 1000.0), 0.05)
    }

    /**
     * The processor has to agree with the design formulae — a correct filter driven
     * by a wrong difference equation is still a wrong filter. Measured by running a
     * sine through and comparing amplitude, after letting the transient settle.
     */
    @Test
    fun `processing a sine matches the designed magnitude response`() {
        val gainDb = 6.0
        val coefficients = BiquadDesign.peaking(1000.0, gainDb, q = 1.0, sampleRate = sampleRate)
        val filter = Biquad(coefficients)

        var peak = 0.0
        val frames = sampleRate / 2
        for (n in 0 until frames) {
            val input = kotlin.math.sin(2.0 * Math.PI * 1000.0 * n / sampleRate)
            val output = filter.process(input)
            // Skip the first 50 ms while the filter's state fills.
            if (n > sampleRate / 20) peak = maxOf(peak, abs(output))
        }

        assertEquals(gainDb, 20.0 * log10(peak), 0.05)
    }

    @Test
    fun `the identity coefficients pass a signal through untouched`() {
        val filter = Biquad(BiquadCoefficients.Identity)

        listOf(0.5, -0.25, 1.0, 0.0).forEach { sample ->
            assertEquals(sample, filter.process(sample), 0.0)
        }
    }

    @Test
    fun `resetting clears the filter's memory`() {
        val filter = Biquad(BiquadDesign.lowPass(500.0, 0.707, sampleRate))
        repeat(100) { filter.process(1.0) }

        filter.reset()

        // With no history, the first output is just b0 times the input.
        val expected = BiquadDesign.lowPass(500.0, 0.707, sampleRate).b0
        assertEquals(expected, filter.process(1.0), 1e-12)
    }

    private companion object {
        /**
         * Tight on purpose. These are compared against a printed table, so anything
         * looser would let a genuinely different filter pass.
         */
        const val COEFFICIENT_TOLERANCE = 1e-11
    }
}
