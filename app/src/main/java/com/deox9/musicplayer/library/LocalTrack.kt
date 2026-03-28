package com.deox9.musicplayer.library

data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String
)
