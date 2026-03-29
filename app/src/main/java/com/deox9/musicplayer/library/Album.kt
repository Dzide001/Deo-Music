package com.deox9.musicplayer.library

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val trackCount: Int
)
