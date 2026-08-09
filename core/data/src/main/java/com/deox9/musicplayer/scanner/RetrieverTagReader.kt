// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Fallback tag reader that works through a content URI.
 *
 * [EAlvaTagReader] needs a filesystem path, and MediaStore does not always expose
 * one. MediaMetadataRetriever reads through the content resolver instead, so it works
 * wherever the app can open the file at all.
 *
 * It recovers album artist, disc and track numbers, composer and the compilation
 * flag. It cannot see ReplayGain: the platform extractor exposes a fixed set of keys
 * and ReplayGain is not among them, so a file only reachable by URI stays unmeasured
 * until a loudness scan runs over it.
 */
class RetrieverTagReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun read(mediaUri: String?): TrackTags {
        val uri = mediaUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return TrackTags.EMPTY

        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                TrackTags(
                    albumArtist = retriever.value(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                    discNumber = TrackNumbers.parsePositionString(
                        retriever.value(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                    ),
                    trackNumber = TrackNumbers.parsePositionString(
                        retriever.value(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                    ),
                    composer = retriever.value(MediaMetadataRetriever.METADATA_KEY_COMPOSER),
                    isCompilation = retriever
                        .value(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                        ?.let { it.trim() == "1" || it.equals("true", ignoreCase = true) },
                )
            }
        }.getOrDefault(TrackTags.EMPTY)
    }

    private fun MediaMetadataRetriever.value(key: Int): String? =
        runCatching { extractMetadata(key) }.getOrNull()?.takeIf(String::isNotBlank)

    /** MediaMetadataRetriever only became AutoCloseable at API 29. */
    private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T =
        try {
            block(this)
        } finally {
            runCatching { release() }
        }
}
