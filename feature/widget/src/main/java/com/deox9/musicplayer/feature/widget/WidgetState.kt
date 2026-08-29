// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.widget

import com.deox9.musicplayer.player.storage.PlaybackSessionEntity

/**
 * What a widget draws.
 *
 * Deliberately not [PlaybackSessionEntity] itself: that carries the whole queue and a
 * live position, neither of which a widget shows, and both of which would make it
 * redraw far more often than it changes. Position is excluded on purpose — a widget
 * that re-rendered every second would be a battery bug, not a feature.
 */
data class WidgetState(
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val isPlaying: Boolean,
    val hasTrack: Boolean,
) {
    companion object {
        /** Shown before anything has ever played, and after the library is cleared. */
        val Empty = WidgetState(
            title = "Nothing playing",
            artist = "",
            albumArtUri = null,
            isPlaying = false,
            hasTrack = false,
        )
    }
}

/**
 * Maps the persisted session onto what the widget needs.
 *
 * A blank URI means the store has a row but nothing usable in it, which is not the
 * same as "no row" and used to render as a track called "Unknown title".
 */
fun PlaybackSessionEntity?.toWidgetState(): WidgetState {
    if (this == null || uri.isBlank()) return WidgetState.Empty
    return WidgetState(
        title = title.ifBlank { "Unknown title" },
        artist = artist,
        albumArtUri = albumArtUri.takeIf { it.isNotBlank() },
        isPlaying = isPlaying,
        hasTrack = true,
    )
}
