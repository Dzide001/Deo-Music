// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainTest {

    @Test
    fun `the gain is the distance from the reference`() {
        assertEquals(5.0, ReplayGain.gainDbFor(-23.0), TOLERANCE)
        assertEquals(-4.0, ReplayGain.gainDbFor(-14.0), TOLERANCE)
        // A track already at the reference needs no adjustment at all.
        assertEquals(0.0, ReplayGain.gainDbFor(ReplayGain.REFERENCE_LUFS), TOLERANCE)
    }

    @Test
    fun `the tag and the pre-amp add`() {
        val gain = ReplayGain.playbackGainDb(
            tagGainDb = -6.0,
            preAmpDb = 3.0,
            fallbackGainDb = 0.0,
            peak = null,
            preventClipping = false,
        )

        assertEquals(-3.0, gain, TOLERANCE)
    }

    /** Null is "never measured", which is what the fallback exists for. */
    @Test
    fun `an untagged track falls back rather than being left alone`() {
        val gain = ReplayGain.playbackGainDb(
            tagGainDb = null,
            preAmpDb = 0.0,
            fallbackGainDb = -7.0,
            peak = null,
            preventClipping = false,
        )

        assertEquals(-7.0, gain, TOLERANCE)
    }

    /**
     * The case that makes ReplayGain sound worse than not using it: a quiet, heavily
     * compressed track asks for a large boost, and applying it clips every peak. The
     * gain has to be capped by the headroom the peak leaves.
     */
    @Test
    fun `a boost is capped by the headroom the peak leaves`() {
        // Peaking at 0.5 leaves 6 dB of headroom, so +9 dB cannot be applied.
        val gain = ReplayGain.playbackGainDb(
            tagGainDb = 9.0,
            preAmpDb = 0.0,
            fallbackGainDb = 0.0,
            peak = 0.5,
            preventClipping = true,
        )

        assertEquals(6.0206, gain, 0.001)
    }

    @Test
    fun `a gain that already fits is left alone`() {
        val gain = ReplayGain.playbackGainDb(
            tagGainDb = 3.0,
            preAmpDb = 0.0,
            fallbackGainDb = 0.0,
            peak = 0.5,
            preventClipping = true,
        )

        assertEquals(3.0, gain, TOLERANCE)
    }

    /** Material that already clips gets pulled down, not merely prevented from rising. */
    @Test
    fun `a peak above full scale forces an attenuation`() {
        val gain = ReplayGain.playbackGainDb(
            tagGainDb = 0.0,
            preAmpDb = 0.0,
            fallbackGainDb = 0.0,
            peak = 1.5,
            preventClipping = true,
        )

        assertTrue("expected attenuation, got $gain", gain < 0.0)
        assertEquals(-3.5218, gain, 0.001)
    }

    @Test
    fun `clipping prevention off leaves the requested gain intact`() {
        val gain = ReplayGain.playbackGainDb(
            tagGainDb = 9.0,
            preAmpDb = 0.0,
            fallbackGainDb = 0.0,
            peak = 0.5,
            preventClipping = false,
        )

        assertEquals(9.0, gain, TOLERANCE)
    }

    /** An unknown or nonsensical peak must not be treated as zero headroom. */
    @Test
    fun `a missing or zero peak does not silence the track`() {
        listOf(null, 0.0).forEach { peak ->
            val gain = ReplayGain.playbackGainDb(
                tagGainDb = 4.0,
                preAmpDb = 0.0,
                fallbackGainDb = 0.0,
                peak = peak,
                preventClipping = true,
            )
            assertEquals("peak=$peak", 4.0, gain, TOLERANCE)
        }
    }

    @Test
    fun `decibels convert to the linear multiplier a mixer wants`() {
        assertEquals(1.0, ReplayGain.dbToLinear(0.0), TOLERANCE)
        assertEquals(0.5, ReplayGain.dbToLinear(-6.0206), 0.001)
        assertEquals(2.0, ReplayGain.dbToLinear(6.0206), 0.001)
    }

    @Test
    fun `a measurement that gated out entirely is not worth writing`() {
        val silent = LoudnessResult(integratedLufs = null, samplePeak = 0.0, gatedBlockCount = 0)
        val measured = LoudnessResult(integratedLufs = -19.4, samplePeak = 0.98, gatedBlockCount = 812)

        assertFalse(ReplayGain.isMeasurementUsable(silent))
        assertTrue(ReplayGain.isMeasurementUsable(measured))
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
