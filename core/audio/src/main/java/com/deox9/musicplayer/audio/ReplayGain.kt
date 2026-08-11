// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.log10
import kotlin.math.pow

/**
 * Turning a loudness measurement into a playback gain.
 *
 * ReplayGain 2.0 defines its reference as -18 LUFS measured to BS.1770, which is what
 * makes a measurement from [LoudnessMeter] directly usable: the gain is simply the
 * distance from the reference. The older ReplayGain 1.0 used a different measure
 * (an RMS-based "equivalent loudness" against 89 dB SPL) and its tags are not
 * interchangeable with these — but in practice both are written to the same tag names,
 * so a player has to accept whatever it finds and hope the writer was consistent.
 */
object ReplayGain {

    /** ReplayGain 2.0's reference level. */
    const val REFERENCE_LUFS = -18.0

    /**
     * The gain to write for a measured loudness.
     *
     * A track quieter than the reference gets a positive gain and vice versa.
     */
    fun gainDbFor(integratedLufs: Double): Double = REFERENCE_LUFS - integratedLufs

    /**
     * The gain to actually apply at playback.
     *
     * Three things combine: the tag, the user's pre-amp, and — if peak is known and
     * clipping prevention is on — a reduction that keeps the loudest sample inside
     * full scale. Without that last step a quiet, heavily compressed track asking for
     * +9 dB will clip audibly on every peak, which is worse than the loudness
     * mismatch the gain was fixing.
     */
    fun playbackGainDb(
        tagGainDb: Double?,
        preAmpDb: Double,
        fallbackGainDb: Double,
        peak: Double?,
        preventClipping: Boolean,
    ): Double {
        val base = (tagGainDb ?: fallbackGainDb) + preAmpDb
        if (!preventClipping || peak == null || peak <= 0.0) return base

        val headroomDb = -amplitudeToDb(peak)
        return minOf(base, headroomDb)
    }

    /** Linear multiplier for a gain in decibels, for handing to a mixer. */
    fun dbToLinear(gainDb: Double): Double = 10.0.pow(gainDb / 20.0)

    fun amplitudeToDb(amplitude: Double): Double =
        if (amplitude <= 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(amplitude)

    /**
     * Whether a measurement is worth writing as a tag.
     *
     * A file that gated out entirely — silence, or shorter than one 400 ms block —
     * has no loudness to record, and writing one anyway is how a silent interlude
     * ends up tagged for +80 dB.
     */
    fun isMeasurementUsable(result: LoudnessResult): Boolean = result.integratedLufs != null
}
