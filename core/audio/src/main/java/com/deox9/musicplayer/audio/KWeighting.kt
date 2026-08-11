// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.tan

/**
 * The two-stage pre-filter from ITU-R BS.1770.
 *
 * The standard prints its coefficients for 48 kHz only. Most of the music on a phone
 * is 44.1 kHz, and using the 48 kHz numbers on it shifts both filters — the shelf and
 * the high-pass — by about 9%, which biases every measurement made from it. So the
 * filters are designed from their analogue parameters at whatever rate the file
 * actually is; at 48 kHz that reproduces the published table, which is what the tests
 * assert.
 *
 * Stage 1 is a high shelf standing in for the acoustic effect of a head, stage 2 a
 * high-pass approximating the ear's insensitivity to very low frequencies.
 */
object KWeighting {

    // Analogue prototype parameters. These are the values that, put through the
    // bilinear transform at 48 kHz, give the coefficient table in the standard.
    private const val SHELF_FREQUENCY_HZ = 1681.974450955533
    private const val SHELF_GAIN_DB = 3.999843853973347
    private const val SHELF_Q = 0.7071752369554196

    private const val HIGHPASS_FREQUENCY_HZ = 38.13547087602444
    private const val HIGHPASS_Q = 0.5003270373238773

    /**
     * The exponent relating the shelf's mid-band gain to its high-band gain.
     *
     * Not derivable from the RBJ cookbook — this shelf is the specific one the
     * standard specifies, so it is designed directly rather than through the generic
     * shelf formula.
     */
    private const val SHELF_VB_EXPONENT = 0.4996667741545416

    fun stage1(sampleRate: Int): BiquadCoefficients {
        val k = tan(PI * SHELF_FREQUENCY_HZ / sampleRate)
        val vh = 10.0.pow(SHELF_GAIN_DB / 20.0)
        val vb = vh.pow(SHELF_VB_EXPONENT)
        val kSquared = k * k
        val a0 = 1.0 + k / SHELF_Q + kSquared

        return BiquadCoefficients(
            b0 = (vh + vb * k / SHELF_Q + kSquared) / a0,
            b1 = 2.0 * (kSquared - vh) / a0,
            b2 = (vh - vb * k / SHELF_Q + kSquared) / a0,
            a1 = 2.0 * (kSquared - 1.0) / a0,
            a2 = (1.0 - k / SHELF_Q + kSquared) / a0,
        )
    }

    fun stage2(sampleRate: Int): BiquadCoefficients {
        val k = tan(PI * HIGHPASS_FREQUENCY_HZ / sampleRate)
        val kSquared = k * k
        val a0 = 1.0 + k / HIGHPASS_Q + kSquared

        return BiquadCoefficients(
            b0 = 1.0,
            b1 = -2.0,
            b2 = 1.0,
            a1 = 2.0 * (kSquared - 1.0) / a0,
            a2 = (1.0 - k / HIGHPASS_Q + kSquared) / a0,
        )
    }
}

/** Both K-weighting stages for one channel, with their own running state. */
internal class KWeightingFilter(sampleRate: Int) {
    private val shelf = Biquad(KWeighting.stage1(sampleRate))
    private val highPass = Biquad(KWeighting.stage2(sampleRate))

    fun process(sample: Double): Double = highPass.process(shelf.process(sample))

    fun reset() {
        shelf.reset()
        highPass.reset()
    }
}
