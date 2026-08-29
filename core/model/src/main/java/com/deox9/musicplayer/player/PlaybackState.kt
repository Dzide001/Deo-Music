// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

/**
 * What the UI needs to render playback, read live from the bound MediaController.
 *
 * This replaces reading [com.deox9.musicplayer.player.storage.PlaybackSessionEntity]
 * back out of DataStore. That entity is now only what it says it is — a snapshot
 * persisted so playback can be restored after process death — rather than doubling
 * as the UI's source of truth on a one-second delay.
 */
data class PlaybackState(
    val uri: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUri: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    /** 0 = off, 1 = one, 2 = all — matches Player.REPEAT_MODE_*. */
    val repeatMode: Int = 0,
    val playerVolume: Float = 1f,
    /**
     * Bumped on every emission. The Now Playing auto-expand logic keys off this to
     * notice that something changed, the way it previously keyed off the persisted
     * record's write timestamp.
     */
    val updatedAtMs: Long = 0L,
    /** The last track that would not play, or null if nothing has failed. */
    val error: PlaybackError? = null,
) {
    val hasTrack: Boolean get() = uri.isNotBlank()
}

/**
 * A track that would not play.
 *
 * Carried on [PlaybackState] because a failure is part of what the player is doing.
 * The media session already knew — `state=ERROR(7) ... error=Source error` — and the
 * UI had no way to find out, so a track that failed to decode looked exactly like one
 * sitting at 0:00 waiting to start.
 */
data class PlaybackError(
    val trackUri: String,
    val trackTitle: String,
    /** Why it failed, in words meant for the person listening rather than an error code. */
    val message: String,
    /**
     * Counts up once per failure.
     *
     * Two failures of the same track are two events, so a one-shot notice keyed on the
     * message alone would announce the first and swallow the second.
     */
    val id: Long,
)

data class QueueEntry(
    val uri: String,
    val title: String,
    val artist: String,
)
