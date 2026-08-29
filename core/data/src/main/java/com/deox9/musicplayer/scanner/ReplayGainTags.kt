// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

/**
 * Parses ReplayGain tag values.
 *
 * The values are written as free text and encoders are inconsistent about it: the
 * unit may be present or absent, spaced or not, and the sign may be explicit. All of
 * "-6.48 dB", "-6.48dB", "-6.48" and "+2.30 dB" mean the same thing.
 *
 * Peaks are a linear sample value, normally at or below 1.0 but legitimately above it
 * for material that clips after decoding, so values over 1 are kept rather than
 * clamped — a limiter needs to know the real peak.
 */
object ReplayGainTags {

    const val TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN"
    const val TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK"
    const val ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN"
    const val ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK"

    /** Every key a scanner should look for, in ID3 TXXX and Vorbis comment form. */
    val KEYS = listOf(TRACK_GAIN, TRACK_PEAK, ALBUM_GAIN, ALBUM_PEAK)

    /**
     * Parses a gain value in decibels.
     *
     * Returns null for absent or unparseable values. Null is not 0 dB: 0 dB means
     * "measured, needs no adjustment" while null means "never measured", and a
     * scanner has to be able to tell them apart.
     */
    fun parseGainDb(raw: String?): Float? {
        val cleaned = raw?.trim()?.removeSuffix("dB")?.removeSuffix("DB")?.removeSuffix("db")?.trim()
        if (cleaned.isNullOrEmpty()) return null

        val value = cleaned.removePrefix("+").toFloatOrNull() ?: return null
        return value.takeIf { it.isFinite() && it in MIN_SANE_DB..MAX_SANE_DB }
    }

    /**
     * Parses a linear peak value.
     *
     * Negative peaks are meaningless and indicate a broken tag, so they are rejected
     * rather than made absolute.
     */
    fun parsePeak(raw: String?): Float? {
        val cleaned = raw?.trim()
        if (cleaned.isNullOrEmpty()) return null

        val value = cleaned.toFloatOrNull() ?: return null
        return value.takeIf { it.isFinite() && it >= 0f }
    }

    /**
     * Rejects values far outside what a real measurement produces.
     *
     * A tag reading -300 dB is corrupt, and applying it would silence the track.
     */
    private const val MIN_SANE_DB = -60f
    private const val MAX_SANE_DB = 60f
}
