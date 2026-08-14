// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deox9.musicplayer.database.MusicDatabase
import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.entity.FavouriteEntity
import com.deox9.musicplayer.scanner.LibraryIndexer
import com.deox9.musicplayer.scanner.ScannedTrack
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The round trip against a real database: export, wipe, restore.
 *
 * The pure matching is covered in BackupTest. What only a database shows is whether
 * the ids line up — a backup that exports correctly and restores into the wrong rows
 * would pass every pure test.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: LibraryDao
    private lateinit var repository: BackupRepository

    private val settings = object : BackupSettingsBridge {
        var restored: Map<String, String> = emptyMap()
        override suspend fun export() = mapOf("eq_enabled" to "true")
        override suspend fun restore(values: Map<String, String>): Int {
            restored = values
            return values.size
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.libraryDao()
        repository = BackupRepository(dao, settings)
    }

    @After
    fun tearDown() = database.close()

    private fun track(title: String, path: String, artist: String = "Stonebwoy") = ScannedTrack(
        mediaUri = "content://media/external/audio/media/${title.hashCode()}",
        sourceId = null,
        albumSourceId = null,
        title = title,
        artist = artist,
        albumArtist = artist,
        album = "Anloga Junction",
        genre = null,
        folderPath = path.substringBeforeLast('/'),
        filePath = path,
        trackNumber = null,
        discNumber = null,
        year = null,
        durationMs = 222_000L,
        mimeType = "audio/mpeg",
        sizeBytes = 5_000_000L,
        bitrateKbps = 320,
        dateAddedMs = 0L,
        dateModifiedMs = 0L,
        isCompilation = false,
    )

    private suspend fun seed(): List<Long> {
        LibraryIndexer(dao).index(
            listOf(
                track("Grade 1", "/music/grade1.mp3"),
                track("Putuu", "/music/putuu.mp3"),
            ),
        )
        return dao.allTrackIdentities().map { it.id }
    }

    @Test
    fun `a playlist survives export and restore`() = runTest {
        val ids = seed()
        val playlistId = dao.createPlaylist("Late night", 0L)
        dao.replacePlaylistEntries(playlistId, ids)

        val backup = repository.export(appVersion = "1.0", nowMs = 1_000L)
        assertEquals(1, backup.playlists.size)
        assertEquals(2, backup.playlists.single().tracks.size)

        // A fresh database with the same files, as a new phone would be.
        tearDown()
        setUp()
        seed()
        val report = repository.restore(backup, nowMs = 2_000L)

        assertEquals(1, report.playlistsRestored)
        assertEquals(2, report.playlistTracksMatched)
        assertEquals(0, report.playlistTracksMissing)
        val restored = dao.allPlaylists().single()
        assertEquals("Late night", restored.name)
        assertEquals(2, restored.trackCount)
    }

    @Test
    fun `favourites survive the round trip`() = runTest {
        val ids = seed()
        dao.addFavourite(FavouriteEntity(trackId = ids.first(), addedAtMs = 0L))

        val backup = repository.export("1.0", 1_000L)
        tearDown(); setUp(); seed()
        val report = repository.restore(backup, 2_000L)

        assertEquals(1, report.favouritesMatched)
        assertEquals(1, dao.allFavourites().size)
    }

    /**
     * The case the whole identity scheme exists for: the same music, but the
     * database rows are different because it was re-scanned on another device.
     */
    @Test
    fun `a track missing from the new device is counted rather than dropped silently`() = runTest {
        val ids = seed()
        val playlistId = dao.createPlaylist("Mixed", 0L)
        dao.replacePlaylistEntries(playlistId, ids)
        val backup = repository.export("1.0", 1_000L)

        // Restore onto a library that has only one of the two files.
        tearDown(); setUp()
        LibraryIndexer(dao).index(listOf(track("Grade 1", "/music/grade1.mp3")))
        val report = repository.restore(backup, 2_000L)

        assertEquals(1, report.playlistTracksMatched)
        assertEquals(1, report.playlistTracksMissing)
        assertTrue(report.anythingMissing)
        assertTrue(report.summary(), report.summary().contains("1 track not found"))
    }

    /** An empty playlist with the right name says something was lost; no playlist says nothing. */
    @Test
    fun `a playlist whose tracks are all missing is still created`() = runTest {
        val ids = seed()
        val playlistId = dao.createPlaylist("All gone", 0L)
        dao.replacePlaylistEntries(playlistId, ids)
        val backup = repository.export("1.0", 1_000L)

        tearDown(); setUp()
        val report = repository.restore(backup, 2_000L)

        assertEquals(2, report.playlistTracksMissing)
        assertEquals("All gone", dao.allPlaylists().single().name)
        assertEquals(0, dao.allPlaylists().single().trackCount)
    }

    @Test
    fun `settings go out and come back`() = runTest {
        seed()

        val backup = repository.export("1.0", 1_000L)
        assertEquals(mapOf("eq_enabled" to "true"), backup.settings)

        repository.restore(backup, 2_000L)
        assertEquals(mapOf("eq_enabled" to "true"), settings.restored)
    }

    @Test
    fun `the export carries a path and tags for every track`() = runTest {
        seed()
        val playlistId = dao.createPlaylist("P", 0L)
        dao.replacePlaylistEntries(playlistId, dao.allTrackIdentities().map { it.id })

        val backup = repository.export("1.0", 1_000L)

        backup.playlists.single().tracks.forEach { ref ->
            assertTrue(ref.path, ref.path.startsWith("/music/"))
            assertTrue(ref.title.isNotBlank())
            assertTrue(ref.artist.isNotBlank())
            assertTrue(ref.durationMs > 0)
        }
    }
}
