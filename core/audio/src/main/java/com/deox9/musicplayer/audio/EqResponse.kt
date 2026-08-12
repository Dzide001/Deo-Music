// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.log10
import kotlin.math.pow

/**
 * The combined magnitude response of a set of bands.
 *
 * A parametric equaliser is unusable without this. A graphic equaliser shows its
 * shape in the position of its sliders; a parametric one has three numbers per band
 * whose combined effect is not something anyone can picture — two overlapping bells
 * at +6 dB are +12 dB where they meet, and nothing on a slider says so.
 */
object EqResponse {

    /** The audible range, which is what the curve is drawn across. */
    const val MIN_FREQUENCY_HZ = 20.0
    const val MAX_FREQUENCY_HZ = 20_000.0

    /**
     * Frequencies to evaluate at, spaced evenly on a log scale.
     *
     * Log spacing because hearing is logarithmic: linear spacing would spend half its
     * points above 10 kHz, where almost nothing is adjusted, and leave the octaves
     * that matter with a handful between them.
     */
    fun logSpacedFrequencies(count: Int): DoubleArray {
        require(count >= 2) { "need at least two points, asked for $count" }
        val minLog = log10(MIN_FREQUENCY_HZ)
        val maxLog = log10(MAX_FREQUENCY_HZ)
        val step = (maxLog - minLog) / (count - 1)
        return DoubleArray(count) { 10.0.pow(minLog + step * it) }
    }

    /**
     * Total response in decibels at each frequency.
     *
     * Summed in decibels, which is the same as multiplying the magnitudes — filters
     * in series multiply. Adding the linear magnitudes instead is a mistake that
     * looks plausible on a single band and is badly wrong on two.
     */
    fun magnitudeDb(
        bands: List<EqBand>,
        sampleRate: Int,
        frequencies: DoubleArray,
    ): DoubleArray {
        val active = bands.filterNot { it.isTransparent }
        val response = DoubleArray(frequencies.size)
        if (active.isEmpty()) return response

        for (band in active) {
            val coefficients = band.coefficients(sampleRate)
            for (index in frequencies.indices) {
                val magnitude = coefficients.magnitudeAt(frequencies[index], sampleRate)
                // A magnitude of zero is a complete null, which is -infinity dB and
                // would make the whole curve unplottable; floored instead.
                response[index] += if (magnitude <= 0.0) {
                    FLOOR_DB
                } else {
                    DB_PER_AMPLITUDE_DECADE * log10(magnitude)
                }
            }
        }
        return response
    }

    /** Deepest cut worth drawing; below this the curve is off the bottom anyway. */
    const val FLOOR_DB = -60.0

    private const val DB_PER_AMPLITUDE_DECADE = 20.0
}
