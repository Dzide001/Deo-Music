// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deox9.musicplayer.database.dao.LibraryDao
import com.deox9.musicplayer.database.entity.AlbumEntity
import com.deox9.musicplayer.database.entity.ArtistEntity
import com.deox9.musicplayer.database.entity.FavouriteEntity
import com.deox9.musicplayer.database.entity.PlayHistoryEntity
import com.deox9.musicplayer.database.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: LibraryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.libraryDao()
    }

    @After
    fun tearDown() = database.close()

    private fun track(
        title: String,
        uri: String,
        albumId: Long? = null,
        disc: Int? = null,
        trackNo: Int? = null,
    ) = TrackEntity(
        title = title,
        sortTitle = title.lowercase(),
        mediaUri = uri,
        artistId = null,
        albumId = albumId,
        genreId = null,
        folderId = null,
        discNumber = disc,
        trackNumber = trackNo,
    )

    @Test
    fun `upserts and reads back tracks`() = runTest {
        dao.upsertTracks(listOf(track("Alpha", "uri://a"), track("Beta", "uri://b")))

        assertEquals(2, dao.trackCount())
        assertEquals("Alpha", dao.trackByUri("uri://a")?.title)
    }

    @Test
    fun `a rescan updates tags in place rather than duplicating`() = runTest {
        dao.upsertScannedTracks(listOf(track("Original", "uri://a")))
        dao.upsertScannedTracks(listOf(track("Retagged", "uri://a")))

        assertEquals(1, dao.trackCount())
        assertEquals("Retagged", dao.trackByUri("uri://a")?.title)
    }

    /**
     * The reason upsertScannedTracks exists. A REPLACE-based upsert would delete the
     * old row first and cascade the user's favourites and history away on every
     * rescan; re-scanning must be non-destructive.
     */
    @Test
    fun `a rescan preserves favourites and play history`() = runTest {
        dao.upsertScannedTracks(listOf(track("Original", "uri://a")))
        val id = dao.trackByUri("uri://a")!!.id
        dao.addFavourite(FavouriteEntity(trackId = id, addedAtMs = 1))
        dao.insertPlay(PlayHistoryEntity(trackId = id, playedAtMs = 1, playedMs = 1000, completed = true))

        dao.upsertScannedTracks(listOf(track("Retagged", "uri://a")))

        assertEquals(id, dao.trackByUri("uri://a")?.id)
        assertEquals(1, dao.observeFavourites().first().size)
        assertEquals(listOf("Retagged"), dao.mostPlayedSince(sinceMs = 0, limit = 10).map { it.title })
    }

    /** Multi-disc albums must order by disc, then track — not by title. */
    @Test
    fun `album tracks order by disc then track number`() = runTest {
        val artistId = dao.upsertArtists(listOf(ArtistEntity(name = "Artist", sortName = "artist"))).first()
        val albumId = dao.upsertAlbums(
            listOf(AlbumEntity(title = "Album", sortTitle = "album", albumArtistId = artistId)),
        ).first()

        dao.upsertTracks(
            listOf(
                track("Zulu", "uri://d2t1", albumId, disc = 2, trackNo = 1),
                track("Alpha", "uri://d1t2", albumId, disc = 1, trackNo = 2),
                track("Mike", "uri://d1t1", albumId, disc = 1, trackNo = 1),
            ),
        )

        val ordered = dao.observeAlbumTracks(albumId).first().map { it.title }

        assertEquals(listOf("Mike", "Alpha", "Zulu"), ordered)
    }

    @Test
    fun `full text search matches on a prefix`() = runTest {
        dao.upsertTracks(
            listOf(
                track("Bohemian Rhapsody", "uri://a"),
                track("Radio Ga Ga", "uri://b"),
            ),
        )

        val hits = dao.searchTracks("Bohem*").map { it.title }

        assertEquals(listOf("Bohemian Rhapsody"), hits)
    }

    @Test
    fun `full text index follows deletes`() = runTest {
        dao.upsertTracks(listOf(track("Bohemian Rhapsody", "uri://a")))
        dao.deleteTracksByUri(listOf("uri://a"))

        assertTrue(dao.searchTracks("Bohem*").isEmpty())
    }

    @Test
    fun `deleting a track cascades to favourites and history`() = runTest {
        dao.upsertTracks(listOf(track("Alpha", "uri://a")))
        val id = dao.trackByUri("uri://a")!!.id

        dao.addFavourite(FavouriteEntity(trackId = id, addedAtMs = 1))
        dao.insertPlay(PlayHistoryEntity(trackId = id, playedAtMs = 1, playedMs = 1000, completed = true))

        dao.deleteTracksByUri(listOf("uri://a"))

        assertNull(dao.trackByUri("uri://a"))
        assertTrue(dao.observeFavourites().first().isEmpty())
    }

    @Test
    fun `most played ranks by play count within the window`() = runTest {
        dao.upsertTracks(listOf(track("Popular", "uri://a"), track("Rare", "uri://b")))
        val popular = dao.trackByUri("uri://a")!!.id
        val rare = dao.trackByUri("uri://b")!!.id

        repeat(3) { dao.insertPlay(PlayHistoryEntity(trackId = popular, playedAtMs = 100, playedMs = 1, completed = true)) }
        dao.insertPlay(PlayHistoryEntity(trackId = rare, playedAtMs = 100, playedMs = 1, completed = true))

        val ranked = dao.mostPlayedSince(sinceMs = 0, limit = 10).map { it.title }

        assertEquals(listOf("Popular", "Rare"), ranked)
    }

    @Test
    fun `never played excludes anything with history`() = runTest {
        dao.upsertTracks(listOf(track("Heard", "uri://a"), track("Unheard", "uri://b")))
        val heard = dao.trackByUri("uri://a")!!.id
        dao.insertPlay(PlayHistoryEntity(trackId = heard, playedAtMs = 1, playedMs = 1, completed = true))

        assertEquals(listOf("Unheard"), dao.neverPlayed().map { it.title })
    }

    @Test
    fun `folder replacement removes only tracks missing from that folder`() = runTest {
        val folders = dao.upsertFolders(
            listOf(
                com.deox9.musicplayer.database.entity.FolderEntity(path = "/music/a", name = "a"),
                com.deox9.musicplayer.database.entity.FolderEntity(path = "/music/b", name = "b"),
            ),
        )
        val folderA = folders[0]
        val folderB = folders[1]

        dao.upsertTracks(
            listOf(
                track("A1", "uri://a1").copy(folderId = folderA),
                track("A2", "uri://a2").copy(folderId = folderA),
                track("B1", "uri://b1").copy(folderId = folderB),
            ),
        )

        // Rescan of folder A finds only A1.
        dao.replaceFolderContents(folderA, listOf(track("A1", "uri://a1").copy(folderId = folderA)))

        assertNull(dao.trackByUri("uri://a2"))
        assertEquals("A1", dao.trackByUri("uri://a1")?.title)
        assertEquals("B1", dao.trackByUri("uri://b1")?.title)
    }
}
