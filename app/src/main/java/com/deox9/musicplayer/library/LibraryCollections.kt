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
