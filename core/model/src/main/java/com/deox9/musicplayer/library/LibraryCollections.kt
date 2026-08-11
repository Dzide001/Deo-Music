// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

data class PlaylistInfo(
    val id: Long,
    val name: String,
    val trackCount: Int
)

data class GenreInfo(
    val id: Long,
    val name: String,
    val trackCount: Int
)

data class FolderInfo(
    val path: String,
    val name: String,
    val trackCount: Int
)

/**
 * An artist as the Artists tab shows it.
 *
 * Album count comes from the tracks' albums rather than from album-artist rows: a
 * featured-on appearance should still put the album in the artist's list, and the
 * album-artist column deliberately does not record those.
 */
data class ArtistInfo(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int
)

/**
 * The loudness figures known for a track.
 *
 * Null gain means never measured, which is not the same as measured at 0 dB: the
 * first is left alone at playback, the second is a deliberate no-change. Kept as a
 * model type rather than passing the database projection outward, so the feature
 * modules do not need to see the schema.
 */
data class TrackLoudness(
    val trackGainDb: Float?,
    val trackPeak: Float?,
    val albumGainDb: Float?,
    val albumPeak: Float?,
) {
    val isMeasured: Boolean get() = trackGainDb != null
}
