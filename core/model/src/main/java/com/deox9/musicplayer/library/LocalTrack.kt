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
    /**
     * Stars, 0-5, or null when the file carries no rating.
     *
     * Null and zero are different answers and both are worth keeping: null is "no
     * rating in the file", zero would be a rating of zero, which POPM has no way to
     * express.
     */
    val rating: Int? = null,
)
