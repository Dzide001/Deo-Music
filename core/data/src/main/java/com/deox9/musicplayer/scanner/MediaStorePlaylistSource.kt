// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reads playlists and their membership from MediaStore.
 *
 * `MediaStore.Audio.Playlists` is deprecated from API 30 in favour of files tagged
 * with a playlist MIME type, but the deprecated surface still works on every
 * supported OS version and is what the app already used before this scanner existed,
 * so it stays the source for now rather than adding a second code path.
 */
class MediaStorePlaylistSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun readPlaylists(): List<ScannedPlaylist> {
        val playlists = mutableListOf<ScannedPlaylist>()

        @Suppress("DEPRECATION")
        val collection = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI

        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME,
        )

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)

            @Suppress("DEPRECATION")
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol).orEmpty().ifBlank { "Untitled playlist" }
                playlists += ScannedPlaylist(
                    mediaStoreId = id,
                    name = name,
                    memberContentUris = readMembers(id),
                )
            }
        }

        return playlists
    }

    private fun readMembers(playlistId: Long): List<String> {
        val members = mutableListOf<String>()

        @Suppress("DEPRECATION")
        val collection = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)

        @Suppress("DEPRECATION")
        val projection = arrayOf(MediaStore.Audio.Playlists.Members.AUDIO_ID)

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            @Suppress("DEPRECATION") "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC",
        )?.use { cursor ->
            val audioIdCol = cursor.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.AUDIO_ID,
            )
            while (cursor.moveToNext()) {
                val audioId = cursor.getLong(audioIdCol)
                members += ContentUris
                    .withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioId)
                    .toString()
            }
        }

        return members
    }
}
