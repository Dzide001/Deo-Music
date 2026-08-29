// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/**
 * A look-ahead peak limiter on the output bus.
 *
 * It exists because everything upstream can raise the signal: a ReplayGain boost, an
 * equaliser band, or both. Without something catching the result, a +6 dB band on
 * material already peaking near full scale clips on every transient, and digital
 * clipping is the one artefact listeners reliably describe as "broken" rather than
 * "different".
 *
 * The look-ahead is what makes it a limiter rather than a hope. Gain is computed from
 * samples that have not been output yet, so by the time a transient arrives the gain
 * is already down; without it, the attack cannot start until the peak has passed and
 * some of it always escapes.
 *
 * The knee is soft, so it starts easing the signal down before the threshold rather
 * than switching on abruptly — a hard knee is audible as a click on the first sample
 * that crosses.
 */
class SoftLimiter(
    private val sampleRate: Int,
    private val channelCount: Int,
    thresholdDb: Double = DEFAULT_THRESHOLD_DB,
    private val kneeDb: Double = DEFAULT_KNEE_DB,
    attackMs: Double = DEFAULT_ATTACK_MS,
    releaseMs: Double = DEFAULT_RELEASE_MS,
) {
    private val thresholdDb = thresholdDb
    private val lookAheadFrames = ((attackMs / MS_PER_SECOND) * sampleRate).toInt().coerceAtLeast(1)

    // One-pole smoothing coefficients. Attack is fast, release slow, which is what
    // stops the limiter from audibly pumping on sustained material.
    private val attackCoefficient = smoothingCoefficient(attackMs)
    private val releaseCoefficient = smoothingCoefficient(releaseMs)

    private val delayLine = FloatArray(lookAheadFrames * channelCount)
    private var delayIndex = 0
    private var envelopeDb = 0.0

    /**
     * The loudest frame anywhere in the look-ahead window, held for as long as it is
     * still coming.
     *
     * Looking at the current sample alone is not enough, and this is the bug it
     * causes: a one-sample transient raises the target for exactly one sample, the
     * envelope moves a fraction of the way, and then the target drops back to zero
     * and it releases — so by the time the spike reaches the output the gain is back
     * at unity and essentially all of it escapes. Holding the peak keeps the demand
     * up for the whole window, which is the time the attack was given to work in.
     */
    private val peakWindow = FloatArray(lookAheadFrames)
    private var peakWindowMax = 0f

    /** Largest gain reduction applied since the last [reset], for a signal-chain readout. */
    var maxGainReductionDb: Double = 0.0
        private set

    fun reset() {
        delayLine.fill(0f)
        peakWindow.fill(0f)
        delayIndex = 0
        peakWindowMax = 0f
        envelopeDb = 0.0
        maxGainReductionDb = 0.0
    }

    /**
     * Limits in place.
     *
     * Output lags input by the look-ahead, which is a constant few milliseconds of
     * latency on the whole stream — inaudible, and the price of the limiter working.
     */
    fun process(interleaved: FloatArray, frameCount: Int) {
        var index = 0
        repeat(frameCount) {
            // The loudest channel decides the gain, and every channel gets the same
            // reduction. Per-channel gain would move the stereo image whenever one
            // side happened to be louder.
            var peak = 0f
            for (channel in 0 until channelCount) {
                val magnitude = abs(interleaved[index + channel])
                if (magnitude > peak) peak = magnitude
            }

            val windowPeak = pushPeak(peak)

            val targetDb = gainReductionFor(windowPeak.toDouble())
            envelopeDb = if (targetDb < envelopeDb) {
                attackCoefficient * envelopeDb + (1.0 - attackCoefficient) * targetDb
            } else {
                releaseCoefficient * envelopeDb + (1.0 - releaseCoefficient) * targetDb
            }

            val delayBase = delayIndex * channelCount

            // What the frame about to leave actually needs. A one-pole envelope
            // approaches its target and never quite arrives, so on its own it lets a
            // fraction of a decibel through; taking the lower of the two makes the
            // threshold a guarantee rather than an aspiration. It only ever bites
            // when the smoothing has lagged, so it is a backstop, not the mechanism.
            var outgoingPeak = 0f
            for (channel in 0 until channelCount) {
                val magnitude = abs(delayLine[delayBase + channel])
                if (magnitude > outgoingPeak) outgoingPeak = magnitude
            }
            val appliedDb = minOf(envelopeDb, gainReductionFor(outgoingPeak.toDouble()))
            if (appliedDb < maxGainReductionDb) maxGainReductionDb = appliedDb

            val gain = 10.0.pow(appliedDb / DB_PER_AMPLITUDE_DECADE).toFloat()

            for (channel in 0 until channelCount) {
                val delayed = delayLine[delayBase + channel]
                delayLine[delayBase + channel] = interleaved[index + channel]
                interleaved[index + channel] = delayed * gain
            }

            delayIndex = (delayIndex + 1) % lookAheadFrames
            index += channelCount
        }
    }

    /**
     * How far down this sample has to come, in decibels.
     *
     * Zero below the knee, a quadratic taper through it, and straight limiting above:
     * an infinite ratio, because the point is that nothing gets out above threshold.
     */
    private fun gainReductionFor(peak: Double): Double {
        if (peak <= 0.0) return 0.0
        val levelDb = DB_PER_AMPLITUDE_DECADE * log10(peak)
        val overshoot = levelDb - thresholdDb

        return when {
            overshoot <= -kneeDb / 2.0 -> 0.0
            overshoot >= kneeDb / 2.0 -> -overshoot
            else -> {
                val x = overshoot + kneeDb / 2.0
                -(x * x) / (2.0 * kneeDb)
            }
        }
    }

    /**
     * Slides the window along and returns the largest frame peak still inside it.
     *
     * The full rescan only happens when the frame leaving the window was the one
     * holding the maximum, so over a run of samples it costs close to nothing.
     */
    private fun pushPeak(peak: Float): Float {
        val leaving = peakWindow[delayIndex]
        peakWindow[delayIndex] = peak

        if (peak >= peakWindowMax) {
            peakWindowMax = peak
        } else if (leaving >= peakWindowMax) {
            var max = 0f
            for (value in peakWindow) if (value > max) max = value
            peakWindowMax = max
        }
        return peakWindowMax
    }

    /** Time constant for a one-pole filter reaching 1 - 1/e of its target. */
    private fun smoothingCoefficient(timeMs: Double): Double =
        if (timeMs <= 0.0) 0.0 else exp(-1.0 / (timeMs / MS_PER_SECOND * sampleRate))

    companion object {
        /**
         * A decibel below full scale. Not zero: inter-sample peaks in the analogue
         * reconstruction can exceed the highest sample, so leaving nothing at all is
         * how a "limited to 0 dBFS" file still clips a DAC.
         */
        const val DEFAULT_THRESHOLD_DB = -1.0
        const val DEFAULT_KNEE_DB = 3.0
        const val DEFAULT_ATTACK_MS = 1.5
        const val DEFAULT_RELEASE_MS = 60.0

        private const val MS_PER_SECOND = 1000.0
        private const val DB_PER_AMPLITUDE_DECADE = 20.0
    }
}
