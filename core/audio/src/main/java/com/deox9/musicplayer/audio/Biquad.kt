// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A second-order IIR section, normalised so a0 is 1.
 *
 * Both the K-weighting pre-filter and the parametric equaliser are built from these,
 * so the design formulae live in one place and are tested once.
 */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    /**
     * Magnitude response at a frequency, as a linear ratio.
     *
     * Evaluating H(z) on the unit circle. Used by the tests to assert that a filter
     * does at 1 kHz what its design parameters claim, which is the only check that
     * catches a transcribed coefficient being subtly wrong.
     */
    fun magnitudeAt(frequencyHz: Double, sampleRate: Int): Double {
        val w = 2.0 * PI * frequencyHz / sampleRate
        val cosW = cos(w)
        val cos2W = cos(2.0 * w)
        val sinW = sin(w)
        val sin2W = sin(2.0 * w)

        val numeratorReal = b0 + b1 * cosW + b2 * cos2W
        val numeratorImag = -(b1 * sinW + b2 * sin2W)
        val denominatorReal = 1.0 + a1 * cosW + a2 * cos2W
        val denominatorImag = -(a1 * sinW + a2 * sin2W)

        val numerator = sqrt(numeratorReal * numeratorReal + numeratorImag * numeratorImag)
        val denominator = sqrt(denominatorReal * denominatorReal + denominatorImag * denominatorImag)
        return numerator / denominator
    }

    companion object {
        /** Passes everything through unchanged. */
        val Identity = BiquadCoefficients(b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0)
    }
}

/**
 * One filter's running state, for one channel.
 *
 * Transposed direct form II: two state variables instead of four, and it is the form
 * least prone to coefficient quantisation trouble at low frequencies — which is where
 * a 38 Hz high-pass and a 30 Hz EQ band both live.
 */
class Biquad(private var coefficients: BiquadCoefficients) {
    private var s1 = 0.0
    private var s2 = 0.0

    fun setCoefficients(value: BiquadCoefficients) {
        coefficients = value
    }

    fun reset() {
        s1 = 0.0
        s2 = 0.0
    }

    fun process(input: Double): Double {
        val output = coefficients.b0 * input + s1
        s1 = coefficients.b1 * input - coefficients.a1 * output + s2
        s2 = coefficients.b2 * input - coefficients.a2 * output
        return output
    }
}

/**
 * Filter design, following the RBJ audio EQ cookbook.
 *
 * These are the shapes a parametric equaliser needs: a peaking band with its own
 * frequency, gain and Q, shelves for the ends, and high/low-pass for cuts. A
 * ten-band graphic EQ is a special case of the peaking filter with the frequencies
 * fixed and Q chosen for you.
 */
object BiquadDesign {

    /** A bell centred on [frequencyHz]. Q is bandwidth: higher is narrower. */
    fun peaking(frequencyHz: Double, gainDb: Double, q: Double, sampleRate: Int): BiquadCoefficients {
        val a = dbToAmplitudeRatio(gainDb)
        val w0 = angularFrequency(frequencyHz, sampleRate)
        val alpha = sin(w0) / (2.0 * q)

        val a0 = 1.0 + alpha / a
        return BiquadCoefficients(
            b0 = (1.0 + alpha * a) / a0,
            b1 = (-2.0 * cos(w0)) / a0,
            b2 = (1.0 - alpha * a) / a0,
            a1 = (-2.0 * cos(w0)) / a0,
            a2 = (1.0 - alpha / a) / a0,
        )
    }

    fun lowShelf(frequencyHz: Double, gainDb: Double, slope: Double, sampleRate: Int): BiquadCoefficients {
        val a = dbToAmplitudeRatio(gainDb)
        val w0 = angularFrequency(frequencyHz, sampleRate)
        val cosW0 = cos(w0)
        val alpha = shelfAlpha(w0, a, slope)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        return BiquadCoefficients(
            b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0,
            b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0) / a0,
            b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0,
            a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0) / a0,
            a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0,
        )
    }

    fun highShelf(frequencyHz: Double, gainDb: Double, slope: Double, sampleRate: Int): BiquadCoefficients {
        val a = dbToAmplitudeRatio(gainDb)
        val w0 = angularFrequency(frequencyHz, sampleRate)
        val cosW0 = cos(w0)
        val alpha = shelfAlpha(w0, a, slope)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        return BiquadCoefficients(
            b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha) / a0,
            b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0) / a0,
            b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0,
            a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0) / a0,
            a2 = ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0,
        )
    }

    fun highPass(frequencyHz: Double, q: Double, sampleRate: Int): BiquadCoefficients {
        val w0 = angularFrequency(frequencyHz, sampleRate)
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val a0 = 1.0 + alpha
        return BiquadCoefficients(
            b0 = ((1.0 + cosW0) / 2.0) / a0,
            b1 = (-(1.0 + cosW0)) / a0,
            b2 = ((1.0 + cosW0) / 2.0) / a0,
            a1 = (-2.0 * cosW0) / a0,
            a2 = (1.0 - alpha) / a0,
        )
    }

    fun lowPass(frequencyHz: Double, q: Double, sampleRate: Int): BiquadCoefficients {
        val w0 = angularFrequency(frequencyHz, sampleRate)
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val a0 = 1.0 + alpha
        return BiquadCoefficients(
            b0 = ((1.0 - cosW0) / 2.0) / a0,
            b1 = (1.0 - cosW0) / a0,
            b2 = ((1.0 - cosW0) / 2.0) / a0,
            a1 = (-2.0 * cosW0) / a0,
            a2 = (1.0 - alpha) / a0,
        )
    }

    /**
     * Peaking and shelving filters work in amplitude, and the cookbook's A is the
     * square root of the power ratio — so this is dB/40, not dB/20. Getting it wrong
     * gives a filter with exactly twice the intended gain, which sounds plausible
     * enough to ship.
     */
    private fun dbToAmplitudeRatio(gainDb: Double): Double = Math.pow(10.0, gainDb / 40.0)

    private fun angularFrequency(frequencyHz: Double, sampleRate: Int): Double =
        2.0 * PI * frequencyHz / sampleRate

    private fun shelfAlpha(w0: Double, a: Double, slope: Double): Double =
        sin(w0) / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)

    /** Converts a Q into the bandwidth form some presets are expressed in. */
    fun qFromBandwidthOctaves(bandwidthOctaves: Double, frequencyHz: Double, sampleRate: Int): Double {
        val w0 = angularFrequency(frequencyHz, sampleRate)
        return 1.0 / (2.0 * sinh(kotlin.math.ln(2.0) / 2.0 * bandwidthOctaves * w0 / sin(w0)))
    }

    /** Only used by the shelf formulae; exposed so tests can pin the identity case. */
    internal fun tanHalfAngle(frequencyHz: Double, sampleRate: Int): Double =
        tan(PI * frequencyHz / sampleRate)
}
