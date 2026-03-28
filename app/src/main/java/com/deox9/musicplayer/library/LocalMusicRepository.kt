package com.deox9.musicplayer.library

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class LocalMusicRepository(
    private val context: Context
) {
    fun getTracks(limit: Int = 1000): List<LocalTrack> {
        val tracks = mutableListOf<LocalTrack>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext() && tracks.size < limit) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                val album = cursor.getString(albumCol).orEmpty()
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(collection, id).toString()

                tracks += LocalTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = duration,
                    contentUri = contentUri
                )
            }
        }

        return tracks
    }
}
