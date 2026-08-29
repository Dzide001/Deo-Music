// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

class CrossfadeTest {

    private companion object {
        const val TOLERANCE = 1e-9
        const val SAMPLES = 501
    }

    private fun progressPoints() = (0 until SAMPLES).map { it.toDouble() / (SAMPLES - 1) }

    // ---- endpoints ----------------------------------------------------------

    /**
     * Every curve has to start and finish exactly, not nearly. A fade that ends at
     * 0.001 instead of 0 leaves a step to silence, which is the click the fade was
     * added to avoid.
     */
    @Test
    fun `every curve begins and ends exactly`() {
        CrossfadeCurve.entries.forEach { curve ->
            assertEquals("${curve.name} in at 0", 0.0, curve.gainIn(0.0), TOLERANCE)
            assertEquals("${curve.name} in at 1", 1.0, curve.gainIn(1.0), TOLERANCE)
            assertEquals("${curve.name} out at 0", 1.0, curve.gainOut(0.0), TOLERANCE)
            assertEquals("${curve.name} out at 1", 0.0, curve.gainOut(1.0), TOLERANCE)
        }
    }

    @Test
    fun `every curve moves in one direction only`() {
        CrossfadeCurve.entries.forEach { curve ->
            var previousIn = -1.0
            var previousOut = 2.0
            progressPoints().forEach { t ->
                val gainIn = curve.gainIn(t)
                val gainOut = curve.gainOut(t)
                assertTrue("${curve.name} in fell at $t", gainIn >= previousIn - TOLERANCE)
                assertTrue("${curve.name} out rose at $t", gainOut <= previousOut + TOLERANCE)
                previousIn = gainIn
                previousOut = gainOut
            }
        }
    }

    @Test
    fun `no curve leaves the usable range`() {
        CrossfadeCurve.entries.forEach { curve ->
            progressPoints().forEach { t ->
                assertTrue("${curve.name} in out of range at $t", curve.gainIn(t) in 0.0..1.0)
                assertTrue("${curve.name} out of range at $t", curve.gainOut(t) in 0.0..1.0)
            }
        }
    }

    @Test
    fun `progress outside zero to one is clamped rather than extrapolated`() {
        CrossfadeCurve.entries.forEach { curve ->
            assertEquals(curve.gainIn(0.0), curve.gainIn(-5.0), TOLERANCE)
            assertEquals(curve.gainIn(1.0), curve.gainIn(5.0), TOLERANCE)
            assertEquals(curve.gainOut(0.0), curve.gainOut(-5.0), TOLERANCE)
            assertEquals(curve.gainOut(1.0), curve.gainOut(5.0), TOLERANCE)
        }
    }

    // ---- the property that separates the curves -----------------------------

    /**
     * The reason equal-power exists. Two uncorrelated signals add as power, so the
     * sum of squares is what has to stay at 1 across the overlap.
     */
    @Test
    fun `equal power holds constant power across the whole overlap`() {
        progressPoints().forEach { t ->
            val a = CrossfadeCurve.EqualPower.gainOut(t)
            val b = CrossfadeCurve.EqualPower.gainIn(t)

            assertEquals("power at $t", 1.0, a * a + b * b, 1e-12)
        }
    }

    /**
     * The failure linear is usually chosen without noticing: at the midpoint both
     * sides sit at 0.5, which sums to 0.707 of full power — a 3 dB hole.
     */
    @Test
    fun `linear dips three decibels in the middle of an overlap`() {
        val a = CrossfadeCurve.Linear.gainOut(0.5)
        val b = CrossfadeCurve.Linear.gainIn(0.5)

        val powerDb = 10.0 * log10(a * a + b * b)

        assertEquals(-3.0103, powerDb, 1e-4)
    }

    /** Linear is right for a correlated pair, where the two add as amplitude. */
    @Test
    fun `linear holds constant amplitude across the overlap`() {
        progressPoints().forEach { t ->
            val sum = CrossfadeCurve.Linear.gainOut(t) + CrossfadeCurve.Linear.gainIn(t)

            assertEquals("amplitude at $t", 1.0, sum, TOLERANCE)
        }
    }

    /**
     * A logarithmic fade falls at a steady number of decibels per unit of time,
     * which is what makes it sound even. Checked away from the pinned endpoints,
     * where the curve is the pure exponential.
     */
    @Test
    fun `logarithmic falls at a steady rate in decibels`() {
        val steps = listOf(0.2, 0.4, 0.6, 0.8)

        val drops = steps.map { t ->
            val gain = CrossfadeCurve.Logarithmic.gainOut(t)
            20.0 * log10(gain)
        }

        val intervals = drops.zipWithNext { first, second -> second - first }
        intervals.forEach { interval ->
            assertEquals(intervals.first(), interval, 1e-9)
        }
        // A fifth of the way through a 60 dB fade is 12 dB down.
        assertEquals(-12.0, drops.first(), 1e-9)
    }

    /**
     * The distinction that matters when picking one: at the midpoint linear has
     * dropped 6 dB, equal-power 3 dB, and logarithmic 30 dB. They are not
     * interchangeable and a listener can hear which is in use.
     */
    @Test
    fun `the three curves are audibly different at the midpoint`() {
        fun midpointDb(curve: CrossfadeCurve) = 20.0 * log10(curve.gainOut(0.5))

        assertEquals(-6.0206, midpointDb(CrossfadeCurve.Linear), 1e-4)
        assertEquals(-3.0103, midpointDb(CrossfadeCurve.EqualPower), 1e-4)
        assertEquals(-30.0, midpointDb(CrossfadeCurve.Logarithmic), 1e-9)
    }

    /** Fading out is fading in run backwards, for every curve. */
    @Test
    fun `out is in reversed`() {
        CrossfadeCurve.entries.forEach { curve ->
            progressPoints().forEach { t ->
                assertEquals(
                    "${curve.name} at $t",
                    curve.gainIn(1.0 - t),
                    curve.gainOut(t),
                    TOLERANCE,
                )
            }
        }
    }

    // ---- stored names -------------------------------------------------------

    @Test
    fun `a stored curve name round-trips`() {
        CrossfadeCurve.entries.forEach { curve ->
            assertEquals(curve, CrossfadeCurve.fromName(curve.name))
        }
    }

    /** An unknown or absent name must not crash an install that has one stored. */
    @Test
    fun `an unrecognised curve name falls back to equal power`() {
        assertEquals(CrossfadeCurve.EqualPower, CrossfadeCurve.fromName(null))
        assertEquals(CrossfadeCurve.EqualPower, CrossfadeCurve.fromName("Parabolic"))
        assertEquals(CrossfadeCurve.EqualPower, CrossfadeCurve.fromName(""))
    }

    // ---- settings -----------------------------------------------------------

    /**
     * Fading on automatic advance digs a hole in a continuous album, so the
     * listener has to ask for it rather than discover it.
     */
    @Test
    fun `fading is off by default in both situations`() {
        val settings = CrossfadeSettings()

        assertFalse(settings.onSkip)
        assertFalse(settings.onAutoAdvance)
    }

    @Test
    fun `an out-of-range duration is clamped rather than honoured`() {
        assertEquals(
            CrossfadeSettings.MIN_DURATION_MS,
            CrossfadeSettings(durationMs = 0).effectiveDurationMs,
        )
        assertEquals(
            CrossfadeSettings.MAX_DURATION_MS,
            CrossfadeSettings(durationMs = 60_000).effectiveDurationMs,
        )
        assertEquals(1_200, CrossfadeSettings(durationMs = 1_200).effectiveDurationMs)
    }

    // ---- the ramp -----------------------------------------------------------

    @Test
    fun `a ramp converts milliseconds to frames at the sample rate`() {
        val ramp = FadeRamp.of(CrossfadeCurve.Linear, FadeDirection.Out, durationMs = 500, sampleRate = 44_100)

        assertEquals(22_050, ramp.durationFrames)
    }

    /** A rate and duration that would round to nothing still has to fade. */
    @Test
    fun `a ramp is never shorter than a single frame`() {
        val ramp = FadeRamp.of(CrossfadeCurve.Linear, FadeDirection.Out, durationMs = 0, sampleRate = 8_000)

        assertTrue(ramp.durationFrames >= 1)
    }

    @Test
    fun `a ramp starts and finishes at its curve's endpoints`() {
        val out = FadeRamp.of(CrossfadeCurve.EqualPower, FadeDirection.Out, 100, 48_000)
        val into = FadeRamp.of(CrossfadeCurve.EqualPower, FadeDirection.In, 100, 48_000)

        assertEquals(1f, out.gainAt(0), 1e-6f)
        assertEquals(0f, out.gainAt(out.durationFrames), 1e-6f)
        assertEquals(0f, into.gainAt(0), 1e-6f)
        assertEquals(1f, into.gainAt(into.durationFrames), 1e-6f)
    }

    /**
     * A buffer can overrun the end of a fade. Holding the final value there keeps
     * playback at the level the fade left it; wrapping would restart the fade and
     * anything else would be a jump.
     */
    @Test
    fun `a ramp holds its final value past the end`() {
        val ramp = FadeRamp.of(CrossfadeCurve.Linear, FadeDirection.Out, 100, 48_000)

        assertEquals(0f, ramp.gainAt(ramp.durationFrames * 10), 1e-6f)
        assertTrue(ramp.isComplete(ramp.durationFrames))
        assertFalse(ramp.isComplete(ramp.durationFrames - 1))
    }

    /** Step to step, the gain must not jump enough to be heard as a click. */
    @Test
    fun `a ramp changes smoothly from frame to frame`() {
        CrossfadeCurve.entries.forEach { curve ->
            val ramp = FadeRamp.of(curve, FadeDirection.Out, durationMs = 400, sampleRate = 44_100)
            var previous = ramp.gainAt(0)
            var largestStep = 0f

            for (frame in 1..ramp.durationFrames) {
                val gain = ramp.gainAt(frame)
                largestStep = maxOf(largestStep, abs(gain - previous))
                previous = gain
            }

            assertTrue("${curve.name} stepped by $largestStep", largestStep < 0.01f)
        }
    }

    @Test
    fun `a zero-length ramp is rejected rather than silently ignored`() {
        val failure = runCatching { FadeRamp(CrossfadeCurve.Linear, FadeDirection.Out, 0) }

        assertTrue(failure.isFailure)
        assertNotEquals(null, failure.exceptionOrNull())
    }
}
