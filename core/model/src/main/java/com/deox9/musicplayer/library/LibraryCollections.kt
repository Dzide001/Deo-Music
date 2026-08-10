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
