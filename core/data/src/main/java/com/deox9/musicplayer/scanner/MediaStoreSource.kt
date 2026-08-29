// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Reads the library from MediaStore.
 *
 * This is the fast pass. It is quick and needs no per-file I/O, but MediaStore only
 * exposes album artist, disc number and the compilation flag from API 30, and never
 * exposes ReplayGain or sort-order tags at all. A thorough pass that opens files and
 * parses tags fills those in later; the shared [ScannedTrack] shape is what lets both
 * feed the same indexer.
 */
class MediaStoreSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun readTracks(minimumDurationMs: Long = 0L): List<ScannedTrack> {
        val projection = buildList {
            addAll(BASE_PROJECTION)
            // Querying a column the platform does not have throws, so each addition
            // is gated: RELATIVE_PATH arrived in Q, the tag columns in R.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) addAll(API_30_PROJECTION)
        }.toTypedArray()

        val results = mutableListOf<ScannedTrack>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor ->
            val columns = Columns(cursor)
            while (cursor.moveToNext()) {
                val track = columns.read(cursor)
                // Hides stingers, notification sounds and voice memos. Zero means keep
                // everything, since a legitimately short track should not vanish.
                if (minimumDurationMs > 0 && track.durationMs in 1 until minimumDurationMs) continue
                results += track
            }
        }

        return results
    }

    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val mimeType = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val dateModified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val data = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        val albumSourceId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

        // Present only from API 30; getColumnIndex returns -1 below that.
        val albumArtist = cursor.getColumnIndex(ALBUM_ARTIST)
        val cdTrackNumber = cursor.getColumnIndex(CD_TRACK_NUMBER)
        val discNumber = cursor.getColumnIndex(DISC_NUMBER)
        val compilation = cursor.getColumnIndex(COMPILATION)
        val genre = cursor.getColumnIndex(GENRE)
        val bitrate = cursor.getColumnIndex(BITRATE)
        val relativePath = cursor.getColumnIndex(RELATIVE_PATH)

        fun read(cursor: Cursor): ScannedTrack {
            val rowId = cursor.getLong(id)
            val filePath = data.takeIf { it >= 0 }?.let(cursor::getString)

            // Prefer the explicit disc/track columns; fall back to the legacy packed
            // TRACK column, which encodes disc * 1000 + track.
            val legacy = TrackNumbers.fromLegacyTrackColumn(cursor.optInt(track))
            val explicitTrack = TrackNumbers.parsePositionString(cursor.optString(cdTrackNumber))
            val explicitDisc = TrackNumbers.parsePositionString(cursor.optString(discNumber))

            return ScannedTrack(
                mediaUri = ContentUris
                    .withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rowId)
                    .toString(),
                sourceId = rowId,
                // Read as a Long, not an Int widened afterwards. Modern MediaStore
                // album ids are 64-bit hashes — 351612700793126060 on this device —
                // and getInt truncates them to a value the albumart provider has
                // never heard of, so every album silently rendered a placeholder.
                albumSourceId = cursor.optLong(albumSourceId),
                title = cursor.getString(title).orEmpty(),
                artist = cursor.getString(artist)?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                albumArtist = cursor.optString(albumArtist)
                    ?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                album = cursor.getString(album)?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                genre = cursor.optString(genre),
                folderPath = folderPathOf(cursor, filePath),
                filePath = filePath,
                trackNumber = explicitTrack ?: legacy.track,
                discNumber = explicitDisc ?: legacy.disc,
                year = cursor.optInt(year)?.takeIf { it > 0 },
                durationMs = cursor.getLong(duration),
                mimeType = cursor.getString(mimeType),
                sizeBytes = cursor.getLong(size),
                bitrateKbps = cursor.optInt(bitrate)?.takeIf { it > 0 }?.div(1000),
                // MediaStore reports seconds; the rest of the app works in millis.
                dateAddedMs = cursor.getLong(dateAdded) * 1000,
                dateModifiedMs = cursor.getLong(dateModified) * 1000,
                isCompilation = cursor.optString(compilation)?.let { it == "1" } ?: false,
            )
        }

        private fun folderPathOf(cursor: Cursor, filePath: String?): String? {
            cursor.optString(relativePath)?.let { return it.trimEnd('/') }
            return filePath?.let { File(it).parent }
        }

        private fun Cursor.optString(index: Int): String? =
            if (index >= 0 && !isNull(index)) getString(index)?.takeIf(String::isNotBlank) else null

        private fun Cursor.optInt(index: Int): Int? =
            if (index >= 0 && !isNull(index)) getInt(index) else null

        private fun Cursor.optLong(index: Int): Long? =
            if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private companion object {
        val BASE_PROJECTION = listOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
        )

        val API_30_PROJECTION = listOf(
            ALBUM_ARTIST,
            CD_TRACK_NUMBER,
            DISC_NUMBER,
            COMPILATION,
            GENRE,
            BITRATE,
        )
    }
}

// Referenced as literals so the projection can be built without an SDK guard on each
// constant; availability is gated by API level where the projection is assembled.
private const val ALBUM_ARTIST = "album_artist"
private const val CD_TRACK_NUMBER = "cd_track_number"
private const val DISC_NUMBER = "disc_number"
private const val COMPILATION = "compilation"
private const val GENRE = "genre"
private const val BITRATE = "bitrate"
private const val RELATIVE_PATH = "relative_path"
