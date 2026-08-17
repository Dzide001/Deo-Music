// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {

    private var nextId = 0L

    private fun track(title: String, artist: String = "Artist", uri: String = title) =
        LocalTrack(
            id = ++nextId,
            title = title,
            artist = artist,
            album = "Album",
            durationMs = 200_000L,
            contentUri = uri,
        )

    @Test
    fun `a library nobody has played yet has nothing to report`() {
        val stats = listeningStats(listOf(track("One")), RecommendationSignals())

        assertFalse(stats.hasAnything)
        assertEquals(0, stats.totalPlays)
        assertEquals(0, stats.skipPercentage)
        assertTrue(stats.topArtists.isEmpty())
        assertTrue(stats.topTracks.isEmpty())
    }

    @Test
    fun `tracks are ranked by how often they were played`() {
        val tracks = listOf(track("One"), track("Two"), track("Three"))
        val stats = listeningStats(
            tracks,
            RecommendationSignals(trackPlayCounts = mapOf("One" to 2, "Two" to 9, "Three" to 5)),
        )

        assertEquals(listOf("Two", "Three", "One"), stats.topTracks.map { it.track.title })
        assertEquals(listOf(9, 5, 2), stats.topTracks.map { it.count })
        assertEquals(16, stats.totalPlays)
        assertEquals(3, stats.distinctTracksPlayed)
    }

    @Test
    fun `an artist is shown the way their name is actually written`() {
        // The counter lowercases so that spellings collapse into one artist; the
        // library still knows the real casing.
        val stats = listeningStats(
            listOf(track("One", artist = "Sunmisola Agbebi")),
            RecommendationSignals(artistPlayCounts = mapOf("sunmisola agbebi" to 4)),
        )

        assertEquals("Sunmisola Agbebi", stats.topArtists.single().artist)
    }

    @Test
    fun `an artist no longer in the library is still named, as best it can be`() {
        val stats = listeningStats(
            emptyList(),
            RecommendationSignals(artistPlayCounts = mapOf("burna boy" to 3)),
        )

        // Lower case, because nothing left on the device knows any better — but
        // reported rather than dropped, since the listening did happen.
        assertEquals("burna boy", stats.topArtists.single().artist)
        assertEquals(3, stats.topArtists.single().plays)
    }

    @Test
    fun `plays of a deleted track are counted but not listed`() {
        val stats = listeningStats(
            listOf(track("Still here", uri = "here")),
            RecommendationSignals(trackPlayCounts = mapOf("here" to 2, "deleted" to 7)),
        )

        assertEquals(9, stats.totalPlays)
        assertEquals(7, stats.playsOfMissingTracks)
        assertEquals(listOf("Still here"), stats.topTracks.map { it.track.title })
    }

    @Test
    fun `equal counts keep a stable order between openings`() {
        // Two tracks played the same number of times must not swap places each
        // visit; that reads as the numbers moving when nothing has happened.
        val tracks = listOf(track("Beta"), track("Alpha"))
        val counts = mapOf("Beta" to 3, "Alpha" to 3)

        val first = listeningStats(tracks, RecommendationSignals(trackPlayCounts = counts))
        val again = listeningStats(tracks.reversed(), RecommendationSignals(trackPlayCounts = counts))

        assertEquals(listOf("Alpha", "Beta"), first.topTracks.map { it.track.title })
        assertEquals(first.topTracks.map { it.track.title }, again.topTracks.map { it.track.title })
    }

    @Test
    fun `the most skipped are ranked separately from the most played`() {
        val tracks = listOf(track("Loved"), track("Endured"))
        val stats = listeningStats(
            tracks,
            RecommendationSignals(
                trackPlayCounts = mapOf("Loved" to 10),
                trackSkipCounts = mapOf("Endured" to 6),
            ),
        )

        assertEquals(listOf("Loved"), stats.topTracks.map { it.track.title })
        assertEquals(listOf("Endured"), stats.mostSkipped.map { it.track.title })
        assertEquals(6, stats.totalSkips)
    }

    @Test
    fun `the skip rate is a share of everything started`() {
        val stats = listeningStats(
            listOf(track("One"), track("Two")),
            RecommendationSignals(
                trackPlayCounts = mapOf("One" to 3),
                trackSkipCounts = mapOf("Two" to 1),
            ),
        )

        assertEquals(25, stats.skipPercentage)
    }

    @Test
    fun `skipping everything reads as one hundred rather than something unbounded`() {
        val stats = listeningStats(
            listOf(track("One")),
            RecommendationSignals(trackSkipCounts = mapOf("One" to 4)),
        )

        assertEquals(100, stats.skipPercentage)
        assertTrue(stats.hasAnything)
    }

    @Test
    fun `the lists are capped`() {
        val tracks = (1..40).map { track("Track $it", uri = "t$it") }
        val stats = listeningStats(
            tracks,
            RecommendationSignals(trackPlayCounts = (1..40).associate { "t$it" to it }),
        )

        assertEquals(10, stats.topTracks.size)
        // Capping the list must not cap the total.
        assertEquals((1..40).sum(), stats.totalPlays)
    }

    @Test
    fun `a counter that has fallen to zero is not listed`() {
        val stats = listeningStats(
            listOf(track("One"), track("Two")),
            RecommendationSignals(trackPlayCounts = mapOf("One" to 0, "Two" to 1)),
        )

        assertEquals(listOf("Two"), stats.topTracks.map { it.track.title })
        assertEquals(1, stats.distinctTracksPlayed)
    }
}
