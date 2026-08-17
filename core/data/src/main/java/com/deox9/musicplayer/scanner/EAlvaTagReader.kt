// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import com.deox9.musicplayer.library.Popularimeter
import ealvatag.audio.AudioFileIO
import ealvatag.tag.FieldKey
import ealvatag.tag.Tag
import java.io.File
import javax.inject.Inject

/**
 * Reads tags straight out of the file with eAlvaTag.
 *
 * This is the only route to ReplayGain values, sort-order tags and reliable album
 * artist below API 30 — MediaStore exposes none of that. eAlvaTag is LGPLv3, which
 * the decision to license this project GPLv3 is what makes usable.
 *
 * Needs a real filesystem path. From Android 11 an app holding the audio read
 * permission gets direct read access to media files, but a path is not always
 * available, so callers must be able to fall back.
 */
class EAlvaTagReader @Inject constructor() {

    fun read(filePath: String?): TrackTags {
        val file = filePath?.let(::File) ?: return TrackTags.EMPTY
        if (!file.isFile || !file.canRead()) return TrackTags.EMPTY

        // A corrupt header, an unsupported container or a file that vanished mid-scan
        // must skip one track, not abort the whole library pass.
        return runCatching { readTags(file) }.getOrDefault(TrackTags.EMPTY)
    }

    private fun readTags(file: File): TrackTags {
        val tag: Tag = AudioFileIO.read(file).tag.orNull() ?: return TrackTags.EMPTY

        return TrackTags(
            albumArtist = tag.value(FieldKey.ALBUM_ARTIST),
            discNumber = TrackNumbers.parsePositionString(tag.value(FieldKey.DISC_NO)),
            trackNumber = TrackNumbers.parsePositionString(tag.value(FieldKey.TRACK)),
            composer = tag.value(FieldKey.COMPOSER),
            grouping = tag.value(FieldKey.GROUPING),
            comment = tag.value(FieldKey.COMMENT),
            bpm = tag.value(FieldKey.BPM)?.substringBefore('.')?.toIntOrNull(),
            musicalKey = tag.value(FieldKey.KEY),
            isCompilation = tag.value(FieldKey.IS_COMPILATION)?.let(::parseBooleanTag),
            titleSort = tag.value(FieldKey.TITLE_SORT),
            artistSort = tag.value(FieldKey.ARTIST_SORT),
            albumSort = tag.value(FieldKey.ALBUM_SORT),
            replayGainTrackDb = ReplayGainTags.parseGainDb(tag.custom(ReplayGainTags.TRACK_GAIN)),
            replayGainTrackPeak = ReplayGainTags.parsePeak(tag.custom(ReplayGainTags.TRACK_PEAK)),
            replayGainAlbumDb = ReplayGainTags.parseGainDb(tag.custom(ReplayGainTags.ALBUM_GAIN)),
            replayGainAlbumPeak = ReplayGainTags.parsePeak(tag.custom(ReplayGainTags.ALBUM_PEAK)),
            // FieldKey.RATING is POPM on ID3 and the equivalent field elsewhere.
            // What comes back is not on one scale across formats, which is why
            // Popularimeter.parse and not toInt.
            rating = Popularimeter.parse(tag.value(FieldKey.RATING)),
        )
    }

    /** A field key the format does not support throws rather than returning empty. */
    private fun Tag.value(key: FieldKey): String? =
        runCatching { getValue(key).orNull() }.getOrNull()?.takeIf(String::isNotBlank)

    /**
     * Reads a ReplayGain value, which has no FieldKey because it is not a standard
     * field: ID3 stores it in a TXXX frame and Vorbis as a plain comment, both keyed
     * by name. Case varies between encoders, so both spellings are tried.
     */
    private fun Tag.custom(name: String): String? {
        listOf(name, name.lowercase()).forEach { candidate ->
            val hit = runCatching { getFirst(candidate) }.getOrNull()?.takeIf(String::isNotBlank)
            if (hit != null) return hit
        }
        return null
    }

    /** Tags spell the compilation flag as 1/0, true/false or yes/no. */
    private fun parseBooleanTag(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }
}
