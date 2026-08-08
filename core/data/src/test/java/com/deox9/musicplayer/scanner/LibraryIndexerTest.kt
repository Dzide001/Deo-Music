// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deox9.musicplayer.database.MusicDatabase
import com.deox9.musicplayer.database.dao.LibraryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the grouping rules the roadmap calls out as the usual failure cases:
 * compilations shattering into one album per performer, and multi-disc albums
 * splitting or mis-ordering.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryIndexerTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: LibraryDao
    private lateinit var indexer: LibraryIndexer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.libraryDao()
        indexer = LibraryIndexer(dao)
    }

    @After
    fun tearDown() = database.close()

    private fun scanned(
        title: String,
        uri: String,
        artist: String? = null,
        albumArtist: String? = null,
        album: String? = null,
        disc: Int? = null,
        track: Int? = null,
        compilation: Boolean = false,
        genre: String? = null,
        folder: String? = null,
        year: Int? = null,
    ) = ScannedTrack(
        mediaUri = uri,
        sourceId = null,
        albumSourceId = null,
        title = title,
        artist = artist,
        albumArtist = albumArtist,
        album = album,
        genre = genre,
        folderPath = folder,
        filePath = null,
        trackNumber = track,
        discNumber = disc,
        year = year,
        durationMs = 1000,
        mimeType = "audio/mpeg",
        sizeBytes = 1,
        bitrateKbps = null,
        dateAddedMs = 0,
        dateModifiedMs = 0,
        isCompilation = compilation,
    )

    /** The headline failure: a compilation must stay one album. */
    @Test
    fun `a compilation stays one album instead of shattering per performer`() = runTest {
        indexer.index(
            listOf(
                scanned("One", "uri://1", artist = "Artist A", albumArtist = "Various Artists", album = "Now 42"),
                scanned("Two", "uri://2", artist = "Artist B", albumArtist = "Various Artists", album = "Now 42"),
                scanned("Three", "uri://3", artist = "Artist C", albumArtist = "Various Artists", album = "Now 42"),
            ),
        )

        val albums = dao.observeAlbums().first()

        assertEquals(1, albums.size)
        assertEquals("Now 42", albums.single().title)
        assertTrue(albums.single().isCompilation)
    }

    /** Without an album-artist tag, the compilation flag alone must hold it together. */
    @Test
    fun `the compilation flag groups an album with no album artist tag`() = runTest {
        indexer.index(
            listOf(
                scanned("One", "uri://1", artist = "Artist A", album = "Soundtrack", compilation = true),
                scanned("Two", "uri://2", artist = "Artist B", album = "Soundtrack", compilation = true),
            ),
        )

        assertEquals(1, dao.observeAlbums().first().size)
    }

    /** Different artists' "Greatest Hits" must not merge into one album. */
    @Test
    fun `same album title by different artists stays separate`() = runTest {
        indexer.index(
            listOf(
                scanned("A", "uri://1", artist = "Queen", albumArtist = "Queen", album = "Greatest Hits"),
                scanned("B", "uri://2", artist = "Abba", albumArtist = "Abba", album = "Greatest Hits"),
            ),
        )

        assertEquals(2, dao.observeAlbums().first().size)
    }

    @Test
    fun `a multi-disc album is one album ordered by disc then track`() = runTest {
        indexer.index(
            listOf(
                scanned("D2T1", "uri://3", artist = "X", albumArtist = "X", album = "Opus", disc = 2, track = 1),
                scanned("D1T2", "uri://2", artist = "X", albumArtist = "X", album = "Opus", disc = 1, track = 2),
                scanned("D1T1", "uri://1", artist = "X", albumArtist = "X", album = "Opus", disc = 1, track = 1),
            ),
        )

        val album = dao.observeAlbums().first().single()
        val ordered = dao.observeAlbumTracks(album.id).first().map { it.title }

        assertEquals(listOf("D1T1", "D1T2", "D2T1"), ordered)
        assertEquals(2, album.discCount)
    }

    @Test
    fun `album artist gets an artist row even when it performs no track`() = runTest {
        indexer.index(
            listOf(scanned("One", "uri://1", artist = "Guest", albumArtist = "Host", album = "Record")),
        )

        val names = dao.observeArtists().first().map { it.name }

        assertTrue("expected both artists, got $names", names.containsAll(listOf("Guest", "Host")))
    }

    @Test
    fun `sort keys are stored so ordering ignores articles and accents`() = runTest {
        indexer.index(
            listOf(
                scanned("The Zoo", "uri://1"),
                scanned("Ólafur", "uri://2"),
                scanned("Abbey Road", "uri://3"),
            ),
        )

        val ordered = dao.observeTracks().first().map { it.title }

        assertEquals(listOf("Abbey Road", "Ólafur", "The Zoo"), ordered)
    }

    @Test
    fun `a track with no album still indexes`() = runTest {
        indexer.index(listOf(scanned("Loose", "uri://1", artist = "Someone")))

        val track = dao.trackByUri("uri://1")
        assertNotNull(track)
        assertEquals(null, track?.albumId)
    }

    @Test
    fun `a blank title falls back rather than indexing an empty name`() = runTest {
        indexer.index(listOf(scanned("", "uri://1")))

        assertEquals(ScannedTrack.UNKNOWN_TITLE, dao.trackByUri("uri://1")?.title)
    }

    @Test
    fun `genres and folders are resolved and linked`() = runTest {
        indexer.index(
            listOf(scanned("One", "uri://1", genre = "Jazz", folder = "Music/Jazz")),
        )

        assertEquals(listOf("Jazz"), dao.observeGenresList().map { it.name })
        val folder = dao.observeFolders().first().single()
        assertEquals("Music/Jazz", folder.path)
        assertEquals("Jazz", folder.name)
        assertEquals(folder.id, dao.trackByUri("uri://1")?.folderId)
    }

    @Test
    fun `re-indexing the same files does not duplicate anything`() = runTest {
        val tracks = listOf(
            scanned("One", "uri://1", artist = "X", albumArtist = "X", album = "Record", genre = "Rock"),
            scanned("Two", "uri://2", artist = "X", albumArtist = "X", album = "Record", genre = "Rock"),
        )

        indexer.index(tracks)
        indexer.index(tracks)

        assertEquals(2, dao.trackCount())
        assertEquals(1, dao.observeAlbums().first().size)
        assertEquals(1, dao.observeArtists().first().size)
        assertEquals(1, dao.observeGenresList().size)
    }

    @Test
    fun `album year takes the earliest across its tracks`() = runTest {
        indexer.index(
            listOf(
                scanned("A", "uri://1", artist = "X", albumArtist = "X", album = "Record", year = 1975),
                scanned("B", "uri://2", artist = "X", albumArtist = "X", album = "Record", year = 1994),
            ),
        )

        assertEquals(1975, dao.observeAlbums().first().single().year)
    }
}
