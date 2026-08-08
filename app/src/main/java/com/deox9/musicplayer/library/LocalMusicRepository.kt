// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

class LocalMusicRepository(
    private val context: Context
) {
    companion object {
        private const val CACHE_TTL_MS = 20_000L

        /** Upper bound on tracks read in a single library pass. */
        const val TRACK_SCAN_LIMIT = 5000

        private data class CacheEntry<T>(
            val key: String,
            val cachedAtMs: Long,
            val value: T
        )

        private val cacheLock = Any()
        private var tracksCache: CacheEntry<List<LocalTrack>>? = null
        private var albumsCache: CacheEntry<List<Album>>? = null
        private var folderPathsCache: CacheEntry<Map<Long, String>>? = null

        fun invalidateCaches() {
            synchronized(cacheLock) {
                tracksCache = null
                albumsCache = null
                folderPathsCache = null
            }
        }
    }

    fun getTracks(limit: Int = 1000): List<LocalTrack> {
        val cacheKey = "tracks:$limit"
        synchronized(cacheLock) {
            val cached = tracksCache
            if (cached != null && cached.key == cacheKey && (System.currentTimeMillis() - cached.cachedAtMs) <= CACHE_TTL_MS) {
                return cached.value
            }
        }

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

        val result = tracks.toList()
        synchronized(cacheLock) {
            tracksCache = CacheEntry(
                key = cacheKey,
                cachedAtMs = System.currentTimeMillis(),
                value = result
            )
        }
        return result
    }

    fun getAlbums(): List<Album> {
        val cacheKey = "albums"
        synchronized(cacheLock) {
            val cached = albumsCache
            if (cached != null && cached.key == cacheKey && (System.currentTimeMillis() - cached.cachedAtMs) <= CACHE_TTL_MS) {
                return cached.value
            }
        }

        val albums = mutableListOf<Album>()

        val albumCollection = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.ALBUM_ART
        )
        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"

        context.contentResolver.query(
            albumCollection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val countCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            val artCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                val count = cursor.getInt(countCol)
                val artPath = cursor.getString(artCol)
                val artworkUri = if (artPath.isNullOrBlank()) {
                    // Fallback: construct from album ID
                    ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                        id
                    )
                } else {
                    android.net.Uri.parse(artPath)
                }

                albums += Album(
                    id = id,
                    title = title,
                    artist = artist,
                    artworkUri = artworkUri,
                    trackCount = count
                )
            }
        }

        val result = albums.toList()
        synchronized(cacheLock) {
            albumsCache = CacheEntry(
                key = cacheKey,
                cachedAtMs = System.currentTimeMillis(),
                value = result
            )
        }
        return result
    }

    fun getTracksByAlbum(albumId: Long): List<LocalTrack> {
        val tracks = mutableListOf<LocalTrack>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ALBUM_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Audio.Media.TRACK} ASC, ${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
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

    fun getPlaylists(): List<PlaylistInfo> {
        val playlists = mutableListOf<PlaylistInfo>()
        val collection = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME
        )

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Playlists.NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol).orEmpty().ifBlank { "Untitled playlist" }
                val count = countMembers(MediaStore.Audio.Playlists.Members.getContentUri("external", id))
                playlists += PlaylistInfo(
                    id = id,
                    name = name,
                    trackCount = count
                )
            }
        }

        return playlists
    }

    fun getTracksByPlaylist(playlistId: Long): List<LocalTrack> {
        val tracks = mutableListOf<LocalTrack>()
        val collection = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
        val projection = arrayOf(
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.TITLE,
            MediaStore.Audio.Playlists.Members.ARTIST,
            MediaStore.Audio.Playlists.Members.ALBUM,
            MediaStore.Audio.Playlists.Members.DURATION
        )

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                val album = cursor.getString(albumCol).orEmpty()
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()

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

    fun getGenres(): List<GenreInfo> {
        val genres = mutableListOf<GenreInfo>()
        val collection = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Genres._ID,
            MediaStore.Audio.Genres.NAME
        )

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Genres.NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol).orEmpty().ifBlank { "Unknown genre" }
                val count = countMembers(MediaStore.Audio.Genres.Members.getContentUri("external", id))
                genres += GenreInfo(
                    id = id,
                    name = name,
                    trackCount = count
                )
            }
        }

        return genres
    }

    fun getTracksByGenre(genreId: Long): List<LocalTrack> {
        val tracks = mutableListOf<LocalTrack>()
        val collection = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
        val projection = arrayOf(
            MediaStore.Audio.Genres.Members.AUDIO_ID,
            MediaStore.Audio.Genres.Members.TITLE,
            MediaStore.Audio.Genres.Members.ARTIST,
            MediaStore.Audio.Genres.Members.ALBUM,
            MediaStore.Audio.Genres.Members.DURATION
        )

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Genres.Members.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                val album = cursor.getString(albumCol).orEmpty()
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()

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

    fun getFolders(): List<FolderInfo> {
        val foldersByTrackId = queryFolderPathsByTrackId()

        return getTracks(limit = TRACK_SCAN_LIMIT)
            .groupBy { foldersByTrackId[it.id].orEmpty() }
            .entries
            .filter { it.key.isNotBlank() }
            .map { (path, items) ->
                FolderInfo(
                    path = path,
                    name = File(path).name.ifBlank { path },
                    trackCount = items.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getTracksByFolder(folderPath: String): List<LocalTrack> {
        val foldersByTrackId = queryFolderPathsByTrackId()

        return getTracks(limit = TRACK_SCAN_LIMIT)
            .filter { foldersByTrackId[it.id] == folderPath }
            .sortedBy { it.title.lowercase() }
    }

    fun createPlaylist(name: String): Long? {
        if (name.isBlank()) return null
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Playlists.NAME, name.trim())
            }
            val uri = context.contentResolver.insert(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                values
            )
            uri?.lastPathSegment?.toLongOrNull()
        }.getOrNull()
    }

    fun addTrackToPlaylist(playlistId: Long, trackContentUri: String): Boolean {
        val audioId = runCatching { ContentUris.parseId(Uri.parse(trackContentUri)) }.getOrNull() ?: return false
        return runCatching {
            val membersUri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
            val nextPlayOrder = nextPlaylistPlayOrder(playlistId)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, audioId)
                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, nextPlayOrder)
            }
            context.contentResolver.insert(membersUri, values) != null
        }.getOrDefault(false)
    }

    fun deleteTrack(trackContentUri: String): Boolean {
        return runCatching {
            val deleted = context.contentResolver.delete(Uri.parse(trackContentUri), null, null) > 0
            if (deleted) {
                invalidateCaches()
            }
            deleted
        }.getOrDefault(false)
    }

    /**
     * Counts rows in a members collection without hydrating each track.
     *
     * Playlist and genre listings previously called `getTracksBy…(id).size`, which ran a
     * full-projection query and allocated a [LocalTrack] per row just to read the count.
     */
    private fun countMembers(membersUri: Uri): Int {
        return runCatching {
            context.contentResolver.query(
                membersUri,
                arrayOf(MediaStore.Audio.Media._ID),
                null,
                null,
                null
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    private fun nextPlaylistPlayOrder(playlistId: Long): Int {
        val membersUri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
        context.contentResolver.query(
            membersUri,
            arrayOf(MediaStore.Audio.Playlists.Members.PLAY_ORDER),
            null,
            null,
            "${MediaStore.Audio.Playlists.Members.PLAY_ORDER} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val playOrderCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.PLAY_ORDER)
                return cursor.getInt(playOrderCol) + 1
            }
        }
        return 0
    }

    /**
     * Reads every track's folder path in a single cursor pass, keyed by track id.
     *
     * This replaces a per-track query: the previous implementation issued one
     * ContentResolver round trip for each of up to [TRACK_SCAN_LIMIT] tracks.
     */
    private fun queryFolderPathsByTrackId(): Map<Long, String> {
        synchronized(cacheLock) {
            val cached = folderPathsCache
            if (cached != null && (System.currentTimeMillis() - cached.cachedAtMs) <= CACHE_TTL_MS) {
                return cached.value
            }
        }

        val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }

        val paths = mutableMapOf<Long, String>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, relativePathColumn),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val pathCol = cursor.getColumnIndexOrThrow(relativePathColumn)
            while (cursor.moveToNext()) {
                val raw = cursor.getString(pathCol).orEmpty()
                // DATA is a full file path on pre-Q; RELATIVE_PATH is already a directory.
                val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    raw.trimEnd('/')
                } else {
                    raw.substringBeforeLast('/', missingDelimiterValue = "")
                }
                paths[cursor.getLong(idCol)] = folder
            }
        }

        val result = paths.toMap()
        synchronized(cacheLock) {
            folderPathsCache = CacheEntry(
                key = "folder_paths",
                cachedAtMs = System.currentTimeMillis(),
                value = result
            )
        }
        return result
    }
}
