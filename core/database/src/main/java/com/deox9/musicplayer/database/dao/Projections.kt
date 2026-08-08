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
