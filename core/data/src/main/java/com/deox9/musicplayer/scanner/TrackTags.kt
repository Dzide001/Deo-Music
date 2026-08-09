// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

/**
 * Tags read from the file itself, as opposed to what MediaStore chose to expose.
 *
 * Every field is nullable and every null means "the tag was not present", never a
 * default. That distinction is what lets [ScannedTrack.mergedWith] leave the
 * MediaStore value in place instead of overwriting it with an absence.
 */
data class TrackTags(
    val albumArtist: String? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val composer: String? = null,
    val grouping: String? = null,
    val comment: String? = null,
    val bpm: Int? = null,
    val musicalKey: String? = null,
    val isCompilation: Boolean? = null,
    val titleSort: String? = null,
    val artistSort: String? = null,
    val albumSort: String? = null,
    val replayGainTrackDb: Float? = null,
    val replayGainTrackPeak: Float? = null,
    val replayGainAlbumDb: Float? = null,
    val replayGainAlbumPeak: Float? = null,
) {
    val isEmpty: Boolean get() = this == EMPTY

    companion object {
        val EMPTY = TrackTags()
    }
}

/**
 * Overlays file tags onto a MediaStore-derived track.
 *
 * File tags win where present, because they are what the file actually says;
 * MediaStore's copy can be stale or, below API 30, simply absent. Where a tag is
 * missing the MediaStore value survives untouched.
 */
fun ScannedTrack.mergedWith(tags: TrackTags): ScannedTrack {
    if (tags.isEmpty) return this

    return copy(
        albumArtist = tags.albumArtist ?: albumArtist,
        discNumber = tags.discNumber ?: discNumber,
        trackNumber = tags.trackNumber ?: trackNumber,
        composer = tags.composer ?: composer,
        grouping = tags.grouping ?: grouping,
        comment = tags.comment ?: comment,
        bpm = tags.bpm ?: bpm,
        musicalKey = tags.musicalKey ?: musicalKey,
        isCompilation = tags.isCompilation ?: isCompilation,
        titleSort = tags.titleSort ?: titleSort,
        replayGainTrackDb = tags.replayGainTrackDb ?: replayGainTrackDb,
        replayGainTrackPeak = tags.replayGainTrackPeak ?: replayGainTrackPeak,
        replayGainAlbumDb = tags.replayGainAlbumDb ?: replayGainAlbumDb,
        replayGainAlbumPeak = tags.replayGainAlbumPeak ?: replayGainAlbumPeak,
    )
}
