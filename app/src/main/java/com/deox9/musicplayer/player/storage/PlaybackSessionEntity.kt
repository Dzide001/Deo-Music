package com.deox9.musicplayer.player.storage

data class PlaybackSessionEntity(
    val uri: String,
    val title: String,
    val artist: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val queue: List<QueueItem>,
    val currentIndex: Int,
    val updatedAtMs: Long,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // 0=off, 1=one, 2=all
    val playerVolume: Float = 1f,
    val album: String = "",
    val albumArtUri: String = "" // Content URI for album artwork
)

data class QueueItem(
    val uri: String,
    val title: String,
    val artist: String
)
