// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EqResponseTest {

    private val sampleRate = 48_000

    private fun responseAt(bands: List<EqBand>, frequencyHz: Double): Double =
        EqResponse.magnitudeDb(bands, sampleRate, doubleArrayOf(frequencyHz)).first()

    @Test
    fun `no bands is a flat line, not an empty one`() {
        val frequencies = EqResponse.logSpacedFrequencies(64)

        val response = EqResponse.magnitudeDb(emptyList(), sampleRate, frequencies)

        assertEquals(64, response.size)
        assertTrue(response.all { it == 0.0 })
    }

    @Test
    fun `a single band shows its own gain at its own frequency`() {
        val band = EqBand(EqBandType.Peaking, frequencyHz = 1000.0, gainDb = 6.0, q = 2.0)

        assertEquals(6.0, responseAt(listOf(band), 1000.0), 0.01)
    }

    /**
     * The reason the curve exists. Two bells that each ask for +6 dB give +12 dB
     * where they coincide, and nothing about two sliders sitting at +6 says so.
     */
    @Test
    fun `overlapping bands add up`() {
        val bands = listOf(
            EqBand(EqBandType.Peaking, frequencyHz = 1000.0, gainDb = 6.0, q = 1.0),
            EqBand(EqBandType.Peaking, frequencyHz = 1000.0, gainDb = 6.0, q = 1.0),
        )

        assertEquals(12.0, responseAt(bands, 1000.0), 0.02)
    }

    /** Filters in series multiply, so a boost and a matching cut cancel exactly. */
    @Test
    fun `a boost and an equal cut cancel`() {
        val bands = listOf(
            EqBand(EqBandType.Peaking, frequencyHz = 500.0, gainDb = 9.0, q = 1.5),
            EqBand(EqBandType.Peaking, frequencyHz = 500.0, gainDb = -9.0, q = 1.5),
        )

        EqResponse.logSpacedFrequencies(128).forEach { frequency ->
            val value = responseAt(bands, frequency)
            assertTrue("$frequency Hz was $value dB", abs(value) < 0.01)
        }
    }

    @Test
    fun `disabled and zero-gain bands contribute nothing`() {
        val bands = listOf(
            EqBand(EqBandType.Peaking, 1000.0, gainDb = 6.0, enabled = false),
            EqBand(EqBandType.Peaking, 1000.0, gainDb = 0.0),
        )

        assertEquals(0.0, responseAt(bands, 1000.0), 1e-9)
    }

    @Test
    fun `frequencies are log spaced across the audible range`() {
        val frequencies = EqResponse.logSpacedFrequencies(3)

        assertEquals(EqResponse.MIN_FREQUENCY_HZ, frequencies.first(), 0.01)
        assertEquals(EqResponse.MAX_FREQUENCY_HZ, frequencies.last(), 1.0)
        // The midpoint of a log scale is the geometric mean, not the arithmetic one.
        assertEquals(632.5, frequencies[1], 1.0)
    }

    /**
     * Half the points would sit above 10 kHz on a linear scale, leaving the octaves
     * people actually adjust with almost nothing between them.
     */
    @Test
    fun `log spacing puts most points below 2 kHz`() {
        val frequencies = EqResponse.logSpacedFrequencies(100)

        val belowTwoKhz = frequencies.count { it < 2000.0 }
        assertTrue("only $belowTwoKhz of 100 below 2 kHz", belowTwoKhz > 60)
    }

    @Test
    fun `asking for fewer than two points is rejected`() {
        listOf(0, 1, -5).forEach { count ->
            runCatching { EqResponse.logSpacedFrequencies(count) }.fold(
                onSuccess = { throw AssertionError("expected rejection for $count") },
                onFailure = { assertTrue(it is IllegalArgumentException) },
            )
        }
    }

    @Test
    fun `a high-pass cuts below its corner and passes above it`() {
        val bands = listOf(EqBand(EqBandType.HighPass, frequencyHz = 200.0, q = 0.707))

        assertTrue(responseAt(bands, 40.0) < -20.0)
        assertEquals(-3.0, responseAt(bands, 200.0), 0.2)
        assertEquals(0.0, responseAt(bands, 5000.0), 0.2)
    }
}
