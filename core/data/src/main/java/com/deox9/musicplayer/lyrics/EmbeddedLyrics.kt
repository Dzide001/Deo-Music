// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.lyrics

import ealvatag.audio.AudioFileIO
import ealvatag.tag.FieldKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Lyrics carried inside the file.
 *
 * This matters more than it did: online lookup is now off by default, so without a
 * local source the lyrics panel works only for tracks someone already fetched. The
 * lyrics are very often right there in the tag, and reading them tells nobody
 * anything.
 *
 * The roadmap's other local source is an `.lrc` file beside the track, and it is
 * declined for the same reason `folder.jpg` was: a sidecar is not an audio file, so
 * reading one on API 33+ needs a broad storage permission this app has no other use
 * for. The tag is inside the audio file, which the app is already allowed to open.
 */
class EmbeddedLyrics @Inject constructor() {

    /**
     * Lyrics from the file's tag, or null when it carries none.
     *
     * Returns synced lines when the tag holds LRC timings, which is common: the
     * ID3 spec has a separate frame for synchronised lyrics, and almost nothing
     * writes it — taggers put LRC text into the plain lyrics frame instead. Ignoring
     * that would throw away the timings on most files that have any.
     */
    suspend fun read(filePath: String?): LyricsData? = withContext(Dispatchers.IO) {
        val path = filePath?.takeIf { it.isNotBlank() } ?: return@withContext null
        val file = File(path)
        if (!file.isFile || !file.canRead()) return@withContext null

        // A corrupt header or an unsupported container costs this one lookup.
        val raw = runCatching {
            AudioFileIO.read(file).tag.orNull()?.getValue(FieldKey.LYRICS)?.orNull()
        }.getOrNull()

        if (raw.isNullOrBlank()) return@withContext null

        val synced = LrcParser.parse(raw)
        LyricsData(
            // With timings, the plain text is the same lines without them, so a
            // reader that cannot show synced lyrics still shows the words.
            plainLyrics = if (synced.isEmpty()) raw.trim() else synced.joinToString("\n") { it.text },
            syncedLines = synced,
            source = "embedded",
            cached = false,
        )
    }
}

/**
 * LRC timings, wherever they arrive from.
 *
 * Extracted so the embedded reader and the online one agree on what a timestamp
 * means. Two copies of this would drift, and the drift would show up as lyrics that
 * are a beat out on one source and not the other.
 */
object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<SyncedLyricLine> {
        if (raw.isBlank()) return emptyList()

        val lines = mutableListOf<SyncedLyricLine>()
        raw.lineSequence().forEach { row ->
            // The timestamps at the front of the line, and only those: a line may
            // carry several — LRC's way of repeating a chorus without repeating its
            // words — and the text is whatever follows the last of them.
            //
            // Matching the text as "everything after a timestamp" was the obvious
            // way and it is wrong, because it is greedy: on
            // "[00:10.00][01:20.00]Chorus" the first timestamp swallows the second
            // as its lyric, and the repeat is lost.
            val stamps = buildList {
                var cursor = 0
                var match = TIMESTAMP.find(row)
                // Only while each is adjacent to the last, so a timestamp appearing
                // inside the lyric itself is text rather than a repeat.
                while (match != null && match.range.first == cursor) {
                    add(match)
                    cursor = match.range.last + 1
                    match = TIMESTAMP.find(row, cursor)
                }
            }

            if (stamps.isEmpty()) return@forEach

            val text = row.substring(stamps.lastOrNull()?.range?.last?.plus(1) ?: 0).trim()
            if (text.isBlank()) return@forEach

            stamps.forEach { stamp ->
                val (minutes, seconds, fraction) = stamp.destructured
                lines += SyncedLyricLine(timestampMs(minutes, seconds, fraction), text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * A timestamp in milliseconds.
     *
     * The fraction is not always hundredths: two digits is the common case, but
     * three appears too, and one is written by hand. Reading them all as the same
     * unit puts a line up to nine tenths of a second out.
     */
    private fun timestampMs(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = when (fraction.length) {
            1 -> (fraction.toLongOrNull() ?: 0L) * 100L
            2 -> (fraction.toLongOrNull() ?: 0L) * 10L
            3 -> fraction.toLongOrNull() ?: 0L
            else -> 0L
        }
        return (minutes.toLongOrNull() ?: 0L) * 60_000L +
            (seconds.toLongOrNull() ?: 0L) * 1_000L +
            fractionMs
    }
}
