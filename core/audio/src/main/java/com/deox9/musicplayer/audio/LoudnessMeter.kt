// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.abs
import kotlin.math.log10

/**
 * The result of measuring one piece of audio.
 *
 * [integratedLufs] is null when nothing in the material survived gating — silence,
 * or a file short enough that no complete 400 ms block fits. That is not the same as
 * a very quiet measurement, and a caller writing a ReplayGain tag has to be able to
 * tell the difference rather than tagging silence as needing +80 dB.
 */
data class LoudnessResult(
    val integratedLufs: Double?,
    /** Highest absolute sample value seen, linear. Above 1.0 means it already clips. */
    val samplePeak: Double,
    val gatedBlockCount: Int,
)

/**
 * Integrated loudness to ITU-R BS.1770-4, with the gating from EBU R 128.
 *
 * Measuring is only half of it; the gating is what makes the number match how loud
 * something actually sounds. Without it, a track with quiet passages measures quieter
 * than it plays, and normalising to that reading makes it too loud. Two gates run:
 * an absolute one at -70 LUFS that discards silence, then a relative one 10 LU below
 * the mean of what survived, which discards the quiet parts of the programme itself.
 *
 * Samples are fed as interleaved frames. The meter is stateful and single-use per
 * measurement: the filters carry history across blocks, which is the point.
 */
class LoudnessMeter(
    private val sampleRate: Int,
    private val channelCount: Int,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(channelCount in 1..MAX_CHANNELS) { "unsupported channel count $channelCount" }
    }

    private val filters = List(channelCount) { KWeightingFilter(sampleRate) }
    private val weights = DoubleArray(channelCount) { channelWeight(it, channelCount) }

    /** 400 ms blocks overlapping by 75%, so a new one starts every 100 ms. */
    private val blockFrames = (sampleRate * BLOCK_MS / MS_PER_SECOND)
    private val stepFrames = blockFrames / BLOCK_STEPS

    // Squared, K-weighted sums per channel for each 100ms step still in flight.
    private val stepSums = ArrayDeque<DoubleArray>()
    private var currentStep = DoubleArray(channelCount)
    private var framesInStep = 0

    private val blockLoudness = mutableListOf<Double>()
    private val blockMeanSquares = mutableListOf<DoubleArray>()
    private var peak = 0.0

    /**
     * Feeds interleaved frames.
     *
     * [frameCount] is frames, not samples: a stereo buffer of 1024 floats is 512
     * frames. Confusing the two halves or doubles every measurement.
     */
    fun add(interleaved: FloatArray, frameCount: Int) {
        var index = 0
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                val sample = interleaved[index++].toDouble()
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude

                val weighted = filters[channel].process(sample)
                currentStep[channel] += weighted * weighted
            }
            framesInStep++
            if (framesInStep == stepFrames) closeStep()
        }
    }

    private fun closeStep() {
        stepSums.addLast(currentStep)
        currentStep = DoubleArray(channelCount)
        framesInStep = 0

        if (stepSums.size < BLOCK_STEPS) return

        // A whole block is the last four steps; drop the oldest afterwards so the
        // next block overlaps this one by three quarters.
        val meanSquares = DoubleArray(channelCount)
        for (step in stepSums) {
            for (channel in 0 until channelCount) meanSquares[channel] += step[channel]
        }
        for (channel in 0 until channelCount) meanSquares[channel] /= blockFrames.toDouble()

        blockMeanSquares += meanSquares
        blockLoudness += loudnessOf(meanSquares)
        stepSums.removeFirst()
    }

    /**
     * Applies both gates and returns the integrated measurement.
     *
     * Safe to call once; the meter is not reset by it.
     */
    fun result(): LoudnessResult {
        val absoluteGated = blockLoudness.indices.filter { blockLoudness[it] > ABSOLUTE_GATE_LUFS }
        if (absoluteGated.isEmpty()) {
            return LoudnessResult(integratedLufs = null, samplePeak = peak, gatedBlockCount = 0)
        }

        val absoluteMean = meanLoudnessOf(absoluteGated)
        val relativeGate = absoluteMean - RELATIVE_GATE_LU
        val gated = absoluteGated.filter { blockLoudness[it] > relativeGate }
        if (gated.isEmpty()) {
            return LoudnessResult(integratedLufs = null, samplePeak = peak, gatedBlockCount = 0)
        }

        return LoudnessResult(
            integratedLufs = meanLoudnessOf(gated),
            samplePeak = peak,
            gatedBlockCount = gated.size,
        )
    }

    /**
     * The loudness of a set of blocks.
     *
     * Averaged as power and converted once, not averaged in decibels — dB is
     * logarithmic, and averaging it gives a geometric mean, which is a different and
     * wrong answer.
     */
    private fun meanLoudnessOf(indices: List<Int>): Double {
        val mean = DoubleArray(channelCount)
        for (index in indices) {
            val block = blockMeanSquares[index]
            for (channel in 0 until channelCount) mean[channel] += block[channel]
        }
        for (channel in 0 until channelCount) mean[channel] /= indices.size.toDouble()
        return loudnessOf(mean)
    }

    private fun loudnessOf(meanSquares: DoubleArray): Double {
        var sum = 0.0
        for (channel in 0 until channelCount) sum += weights[channel] * meanSquares[channel]
        if (sum <= 0.0) return Double.NEGATIVE_INFINITY
        return LOUDNESS_OFFSET + DECIBELS_PER_DECADE * log10(sum)
    }

    companion object {
        /**
         * Channel weights from BS.1770. Surround channels count for more because they
         * are perceived as louder from the side; everything else is unity.
         */
        private const val SURROUND_WEIGHT = 1.41

        /**
         * The offset that makes a 1 kHz sine read its own dBFS value. It exists to
         * cancel the K-weighting's gain at 1 kHz, which is about +0.69 dB.
         */
        private const val LOUDNESS_OFFSET = -0.691
        private const val DECIBELS_PER_DECADE = 10.0

        const val ABSOLUTE_GATE_LUFS = -70.0
        const val RELATIVE_GATE_LU = 10.0

        private const val BLOCK_MS = 400
        private const val BLOCK_STEPS = 4
        private const val MS_PER_SECOND = 1000
        private const val MAX_CHANNELS = 6

        /**
         * Weights by index, assuming the usual L R C LFE Ls Rs order.
         *
         * Stereo and mono are the cases that actually occur in a phone library, and
         * both are unity; the surround weights are here so a 5.1 file is not measured
         * as if its rear channels were fronts.
         */
        fun channelWeight(channelIndex: Int, channelCount: Int): Double = when {
            channelCount <= 2 -> 1.0
            // LFE is excluded from the measurement entirely.
            channelIndex == LFE_INDEX -> 0.0
            channelIndex >= FIRST_SURROUND_INDEX -> SURROUND_WEIGHT
            else -> 1.0
        }

        private const val LFE_INDEX = 3
        private const val FIRST_SURROUND_INDEX = 4
    }
}
