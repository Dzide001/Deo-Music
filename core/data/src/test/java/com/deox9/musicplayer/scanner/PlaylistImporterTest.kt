// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deox9.musicplayer.database.MusicDatabase
import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the import logic against a real Room database, the same way
 * [LibraryIndexerTest] does — the MediaStore-reading half is a thin, untestable
 * wrapper ([MediaStorePlaylistSource]); everything worth verifying is in how a
 * [ScannedPlaylist] resolves against already-indexed tracks.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistImporterTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: LibraryDao
    private lateinit var importer: PlaylistImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.libraryDao()
        // These tests drive importAll, which takes the scanned playlists as a
        // parameter, so the MediaStore-reading source is constructed but never
        // queried. That keeps the test on the logic worth verifying without
        // introducing an interface that exists only for tests.
        importer = PlaylistImporter(source = MediaStorePlaylistSource(context), dao = dao)
    }

    @After
    fun tearDown() = database.close()

    private fun track(uri: String, title: String = uri) = TrackEntity(
        title = title,
        sortTitle = title.lowercase(),
        mediaUri = uri,
        artistId = null,
        albumId = null,
        genreId = null,
        folderId = null,
    )

    @Test
    fun `imports a playlist and its tracks in order`() = runTest {
        dao.upsertTracks(listOf(track("uri://a"), track("uri://b"), track("uri://c")))

        importer.importAll(
            listOf(ScannedPlaylist(mediaStoreId = 1, name = "Road Trip", memberContentUris = listOf("uri://c", "uri://a", "uri://b"))),
            nowMs = 1_000,
        )

        val playlists = dao.observePlaylistsWithCounts().first()
        assertEquals(1, playlists.size)
        assertEquals("Road Trip", playlists.single().name)
        assertEquals(3, playlists.single().trackCount)

        val ordered = dao.playlistTracksWithNames(playlists.single().id).map { it.title }
        assertEquals(listOf("uri://c", "uri://a", "uri://b"), ordered)
    }

    /** A member the indexer has not seen yet must not fail the whole import. */
    @Test
    fun `silently drops members that are not indexed`() = runTest {
        dao.upsertTracks(listOf(track("uri://a")))

        importer.importAll(
            listOf(ScannedPlaylist(mediaStoreId = 1, name = "Mix", memberContentUris = listOf("uri://a", "uri://missing"))),
            nowMs = 1_000,
        )

        val playlistId = dao.observePlaylistsWithCounts().first().single().id
        assertEquals(listOf("uri://a"), dao.playlistTracksWithNames(playlistId).map { it.title })
    }

    /** The headline behaviour: re-scanning must not duplicate the playlist. */
    @Test
    fun `re-importing the same MediaStore playlist updates it in place`() = runTest {
        dao.upsertTracks(listOf(track("uri://a"), track("uri://b")))
        val scanned = ScannedPlaylist(mediaStoreId = 7, name = "Gym", memberContentUris = listOf("uri://a"))

        importer.importAll(listOf(scanned), nowMs = 1_000)
        val firstId = dao.observePlaylistsWithCounts().first().single().id

        importer.importAll(listOf(scanned), nowMs = 2_000)
        val playlists = dao.observePlaylistsWithCounts().first()

        assertEquals(1, playlists.size)
        assertEquals(firstId, playlists.single().id)
    }

    @Test
    fun `re-importing reflects a changed membership rather than appending to it`() = runTest {
        dao.upsertTracks(listOf(track("uri://a"), track("uri://b")))
        val playlistId1 = ScannedPlaylist(mediaStoreId = 3, name = "Focus", memberContentUris = listOf("uri://a"))
        importer.importAll(listOf(playlistId1), nowMs = 1_000)

        val updated = ScannedPlaylist(mediaStoreId = 3, name = "Focus", memberContentUris = listOf("uri://b"))
        importer.importAll(listOf(updated), nowMs = 2_000)

        val playlistId = dao.observePlaylistsWithCounts().first().single().id
        assertEquals(listOf("uri://b"), dao.playlistTracksWithNames(playlistId).map { it.title })
    }

    @Test
    fun `renaming the MediaStore playlist updates the name on re-import`() = runTest {
        dao.upsertTracks(listOf(track("uri://a")))
        val original = ScannedPlaylist(mediaStoreId = 9, name = "Old Name", memberContentUris = listOf("uri://a"))
        importer.importAll(listOf(original), nowMs = 1_000)

        val renamed = ScannedPlaylist(mediaStoreId = 9, name = "New Name", memberContentUris = listOf("uri://a"))
        importer.importAll(listOf(renamed), nowMs = 2_000)

        val playlists = dao.observePlaylistsWithCounts().first()
        assertEquals(1, playlists.size)
        assertEquals("New Name", playlists.single().name)
    }

    @Test
    fun `imports multiple playlists independently`() = runTest {
        dao.upsertTracks(listOf(track("uri://a"), track("uri://b")))

        importer.importAll(
            listOf(
                ScannedPlaylist(mediaStoreId = 1, name = "One", memberContentUris = listOf("uri://a")),
                ScannedPlaylist(mediaStoreId = 2, name = "Two", memberContentUris = listOf("uri://b")),
            ),
            nowMs = 1_000,
        )

        val names = dao.observePlaylistsWithCounts().first().map { it.name }
        assertTrue(names.containsAll(listOf("One", "Two")))
    }

    @Test
    fun `an empty playlist imports with zero tracks rather than failing`() = runTest {
        importer.importAll(
            listOf(ScannedPlaylist(mediaStoreId = 1, name = "Empty", memberContentUris = emptyList())),
            nowMs = 1_000,
        )

        val playlists = dao.observePlaylistsWithCounts().first()
        assertEquals(1, playlists.size)
        assertEquals(0, playlists.single().trackCount)
    }
}
