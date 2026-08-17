// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPlaylistsTest {

    private fun track(title: String, addedMs: Long = 1_000L, uri: String = title) = LocalTrack(
        id = title.hashCode().toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        durationMs = 200_000L,
        contentUri = uri,
        dateAddedMs = addedMs,
    )

    private fun named(result: List<AutoPlaylist>) = result.map { it.name }

    @Test
    fun `an empty library generates nothing`() {
        assertTrue(autoPlaylists(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun `most played is omitted until something has been played`() {
        val result = autoPlaylists(listOf(track("One")), emptyMap())

        // Recently added still appears — the file has a date even if nobody played it.
        assertEquals(listOf("Recently added"), named(result))
    }

    @Test
    fun `recently added is omitted when no file carries a date`() {
        val result = autoPlaylists(
            listOf(track("One", addedMs = 0L)),
            mapOf("One" to 3),
        )

        assertEquals(listOf("Most played"), named(result))
    }

    @Test
    fun `most played is ordered by play count`() {
        val tracks = listOf(track("One"), track("Two"), track("Three"))
        val result = autoPlaylists(tracks, mapOf("One" to 2, "Two" to 9, "Three" to 5))

        val mostPlayed = result.first { it.kind == AutoPlaylistKind.MostPlayed }
        assertEquals(listOf("Two", "Three", "One"), mostPlayed.tracks.map { it.title })
    }

    @Test
    fun `a track nobody has played is left out of most played`() {
        val tracks = listOf(track("Played"), track("Ignored"))
        val result = autoPlaylists(tracks, mapOf("Played" to 1))

        val mostPlayed = result.first { it.kind == AutoPlaylistKind.MostPlayed }
        assertEquals(listOf("Played"), mostPlayed.tracks.map { it.title })
    }

    @Test
    fun `recently added is newest first`() {
        val tracks = listOf(
            track("Oldest", addedMs = 1_000L),
            track("Newest", addedMs = 9_000L),
            track("Middle", addedMs = 5_000L),
        )
        val result = autoPlaylists(tracks, emptyMap())

        val recent = result.first { it.kind == AutoPlaylistKind.RecentlyAdded }
        assertEquals(listOf("Newest", "Middle", "Oldest"), recent.tracks.map { it.title })
    }

    @Test
    fun `a file with no date is not treated as the oldest thing in the library`() {
        // Zero means MediaStore did not say. Sorting it as a date would bury it, or
        // float it, depending on the direction — so it is excluded from this list.
        val tracks = listOf(track("Undated", addedMs = 0L), track("Dated", addedMs = 5_000L))
        val result = autoPlaylists(tracks, emptyMap())

        val recent = result.first { it.kind == AutoPlaylistKind.RecentlyAdded }
        assertEquals(listOf("Dated"), recent.tracks.map { it.title })
    }

    @Test
    fun `equal counts keep a stable order between openings`() {
        val tracks = listOf(track("Beta"), track("Alpha"))
        val counts = mapOf("Beta" to 3, "Alpha" to 3)

        val first = autoPlaylists(tracks, counts).first().tracks.map { it.title }
        val again = autoPlaylists(tracks.reversed(), counts).first().tracks.map { it.title }

        assertEquals(listOf("Alpha", "Beta"), first)
        assertEquals(first, again)
    }

    @Test
    fun `both lists are capped`() {
        val tracks = (1..80).map { track("Track $it", addedMs = it.toLong()) }
        val result = autoPlaylists(tracks, tracks.associate { it.contentUri to 1 })

        assertTrue(result.all { it.trackCount == 50 })
    }

    @Test
    fun `most played comes before recently added`() {
        val result = autoPlaylists(listOf(track("One")), mapOf("One" to 1))

        assertEquals(listOf("Most played", "Recently added"), named(result))
    }
}
