// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

/**
 * Disc and track numbers, which MediaStore reports in three different shapes.
 */
object TrackNumbers {

    data class Position(val disc: Int?, val track: Int?)

    /**
     * Decodes the legacy [android.provider.MediaStore.Audio.Media.TRACK] column.
     *
     * That column packs disc and track as `disc * 1000 + track`, so a value of 2005 is
     * disc 2, track 5. Reading it as a plain track number is what produces libraries
     * where a two-disc album shows tracks numbered 1001 upward.
     *
     * Values below 1000 have no disc component and are the track number as-is.
     */
    fun fromLegacyTrackColumn(value: Int?): Position {
        if (value == null || value <= 0) return Position(disc = null, track = null)

        return if (value >= DISC_MULTIPLIER) {
            Position(disc = value / DISC_MULTIPLIER, track = (value % DISC_MULTIPLIER).takeIf { it > 0 })
        } else {
            Position(disc = null, track = value)
        }
    }

    /**
     * Parses the modern `CD_TRACK_NUMBER` / `DISC_NUMBER` columns, which are strings
     * and may carry a total: "5", "5/12" and " 5 / 12 " all mean 5.
     */
    fun parsePositionString(value: String?): Int? {
        val head = value?.trim()?.substringBefore('/')?.trim()
        return head?.toIntOrNull()?.takeIf { it > 0 }
    }

    private const val DISC_MULTIPLIER = 1000
}
