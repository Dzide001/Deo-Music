// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.playlist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deox9.musicplayer.database.MusicDatabase
import com.deox9.musicplayer.database.dao.LibraryDao
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

@RunWith(RobolectricTestRunner::class)
class PlaylistTransferTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: LibraryDao
    private lateinit var transfer: PlaylistTransfer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.libraryDao()
        transfer = PlaylistTransfer(dao)
    }

    @After
    fun tearDown() = database.close()

    private fun track(title: String, path: String) = ScannedTrack(
        mediaUri = "content://media/external/audio/media/${title.hashCode()}",
        sourceId = null,
        albumSourceId = null,
        title = title,
        artist = "Stonebwoy",
        albumArtist = "Stonebwoy",
        album = "Anloga Junction",
        genre = null,
        folderPath = path.substringBeforeLast('/'),
        filePath = path,
        trackNumber = null,
        discNumber = null,
        year = null,
        durationMs = 222_000L,
        mimeType = "audio/mpeg",
        sizeBytes = 1L,
        bitrateKbps = 320,
        dateAddedMs = 0L,
        dateModifiedMs = 0L,
        isCompilation = false,
    )

    private suspend fun seed() = LibraryIndexer(dao).index(
        listOf(track("Grade 1", "/music/grade1.mp3"), track("Putuu", "/music/putuu.mp3")),
    )

    @Test
    fun `a playlist exports with a path and a name per track`() = runTest {
        seed()
        val id = dao.createPlaylist("Late night", 0L)
        dao.replacePlaylistEntries(id, dao.allTrackIdentities().map { it.id })

        val text = transfer.export(id)

        assertTrue(text, text.startsWith("#EXTM3U"))
        assertTrue(text, "/music/grade1.mp3" in text.lines())
        assertTrue(text, "#EXTINF:222,Stonebwoy - Grade 1" in text.lines())
    }

    /** The point of the feature: out of this app and back in. */
    @Test
    fun `an exported playlist imports again with every track found`() = runTest {
        seed()
        val id = dao.createPlaylist("Late night", 0L)
        dao.replacePlaylistEntries(id, dao.allTrackIdentities().map { it.id })
        val text = transfer.export(id)

        val result = transfer.import("Reimported", text)

        assertEquals(2, result.matched)
        assertEquals(0, result.missing)
        assertTrue(dao.allPlaylists().any { it.name == "Reimported" && it.trackCount == 2 })
    }

    /** A playlist written on another device, where the paths mean nothing here. */
    @Test
    fun `tracks are matched by name when the paths are from another device`() = runTest {
        seed()
        val foreign = """
            #EXTM3U
            #EXTINF:222,Stonebwoy - Grade 1
            /Users/someone/Music/grade1.mp3
        """.trimIndent()

        val result = transfer.import("From a laptop", foreign)

        assertEquals(1, result.matched)
        assertEquals(0, result.missing)
    }

    @Test
    fun `tracks that are not here are counted rather than dropped quietly`() = runTest {
        seed()
        val text = "#EXTM3U\n/music/grade1.mp3\n/music/nothing-like-this.mp3\n"

        val result = transfer.import("Partial", text)

        assertEquals(1, result.matched)
        assertEquals(1, result.missing)
        assertTrue(result.summary(), "1 not found" in result.summary())
    }

    @Test
    fun `one imported track is not described as plural`() = runTest {
        seed()

        val result = transfer.import("One", "#EXTM3U\n/music/grade1.mp3\n")

        assertTrue(result.summary(), "1 track" in result.summary())
    }
}
