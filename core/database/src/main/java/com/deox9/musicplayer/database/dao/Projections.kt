// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database.dao

/**
 * Read models that join the lookup tables.
 *
 * Lists need the artist and album *names*, not their ids, and doing that lookup per
 * row in Kotlin would be the N+1 pattern this schema exists to remove.
 */
data class TrackWithNames(
    val id: Long,
    val title: String,
    val mediaUri: String,
    val durationMs: Long,
    val artistName: String?,
    val albumTitle: String?,
    val albumId: Long?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val folderPath: String?,
    val genreName: String?,
    val dateAddedMs: Long,
    /**
     * The album's MediaStore id, carried so a row can resolve its own artwork
     * without a second query per track — which is the N+1 this projection exists
     * to avoid.
     */
    val albumMediaStoreId: Long?,
)

data class AlbumWithArtist(
    val id: Long,
    val title: String,
    val artistName: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val year: Int?,
    val isCompilation: Boolean,
    /** MediaStore ALBUM_ID, used to resolve artwork from its albumart provider. */
    val mediaStoreAlbumId: Long?,
)

data class NamedCount(
    val id: Long,
    val name: String,
    val trackCount: Int,
)

data class ArtistWithCounts(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
)

/** Just enough of a track to measure it. */
data class TrackGainTarget(
    val id: Long,
    val mediaUri: String,
)

/**
 * A track's stored ReplayGain figures.
 *
 * All four are nullable and stay that way: null means never measured, which is a
 * different thing from a measured 0 dB, and only one of them can be corrected by
 * running a scan.
 */
data class TrackReplayGain(
    val replayGainTrackDb: Float?,
    val replayGainTrackPeak: Float?,
    val replayGainAlbumDb: Float?,
    val replayGainAlbumPeak: Float?,
)
