// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

/**
 * One audio file as read from a source, before it is resolved into database rows.
 *
 * Kept separate from the Room entities so the MediaStore pass, and later a SAF and
 * tag-parsing pass, can produce the same shape and share all the grouping logic.
 */
data class ScannedTrack(
    val mediaUri: String,
    val sourceId: Long?,
    /** MediaStore ALBUM_ID, which is what its albumart provider is keyed by. */
    val albumSourceId: Long?,
    val title: String,
    val artist: String?,
    /** Album artist from the tag. Null means the tag was absent, not "same as artist". */
    val albumArtist: String?,
    val album: String?,
    val genre: String?,
    val folderPath: String?,
    val filePath: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val durationMs: Long,
    val mimeType: String?,
    val sizeBytes: Long,
    val bitrateKbps: Int?,
    val dateAddedMs: Long,
    val dateModifiedMs: Long,
    val isCompilation: Boolean,

    // Filled by the thorough pass; MediaStore never exposes these.
    val composer: String? = null,
    val grouping: String? = null,
    val comment: String? = null,
    val bpm: Int? = null,
    val musicalKey: String? = null,
    val titleSort: String? = null,
    val replayGainTrackDb: Float? = null,
    val replayGainTrackPeak: Float? = null,
    val replayGainAlbumDb: Float? = null,
    val replayGainAlbumPeak: Float? = null,
    val rating: Int? = null,
) {
    /**
     * The artist an album should be filed under.
     *
     * Falling back to the track artist when the album-artist tag is missing is what
     * shatters a compilation into one album per guest performer. A track explicitly
     * flagged as a compilation is filed under [VARIOUS_ARTISTS] instead.
     */
    fun effectiveAlbumArtist(): String = when {
        !albumArtist.isNullOrBlank() -> albumArtist
        isCompilation -> VARIOUS_ARTISTS
        else -> artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST
    }

    companion object {
        const val VARIOUS_ARTISTS = "Various Artists"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
        const val UNKNOWN_TITLE = "Unknown title"
    }
}
