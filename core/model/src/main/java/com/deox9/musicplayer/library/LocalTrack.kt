// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String,
    /**
     * Artwork for the track's album, as a string so this module stays free of
     * android.net.Uri. Null when the album is unknown or has no art — rows render a
     * placeholder rather than a gap, so a mixed library does not look broken.
     */
    val artworkUri: String? = null,
)
