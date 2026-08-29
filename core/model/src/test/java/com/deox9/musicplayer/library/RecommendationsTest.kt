// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsTest {

    /**
     * Longer than the song-length bonus covers.
     *
     * Needed by the tests that assert on an untouched score: a track of ordinary
     * length already carries the silent +8, so the zero floor is only reachable
     * outside that window.
     */
    private val outsideSongLength = 400_000L

    private var nextId = 0L

    private fun track(
        title: String = "Track",
        artist: String = "Artist",
        durationMs: Long = 200_000L,
        uri: String = "uri-${++nextId}",
    ) = LocalTrack(
        id = nextId,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = durationMs,
        contentUri = uri,
    )

    private val noFavourites = emptySet<String>()

    private fun seed() = SuggestionSeed()

    private fun signals() = RecommendationSignals()

    private fun uris(result: List<SuggestedRecommendation>) =
        result.map { it.track.contentUri }

    private fun scoreOf(result: List<SuggestedRecommendation>, uri: String) =
        result.first { it.track.contentUri == uri }.score

    @Test
    fun `empty library suggests nothing`() {
        val result = recommendTracks(
            tracks = emptyList(),
            seed = SuggestionSeed(),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `the playing track is not suggested back`() {
        val playing = track(uri = "playing")
        val other = track(uri = "other")

        val result = recommendTracks(
            tracks = listOf(playing, other),
            seed = SuggestionSeed(uri = "playing"),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals(listOf("other"), result.map { it.track.contentUri })
    }

    @Test
    fun `a hidden track stays hidden however well it would have scored`() {
        val hidden = track(artist = "Burna Boy", uri = "hidden")
        val plain = track(artist = "Someone Else", uri = "plain")

        val result = recommendTracks(
            tracks = listOf(hidden, plain),
            // Same artist as the seed, so it would otherwise rank first.
            seed = SuggestionSeed(artist = "Burna Boy"),
            signals = RecommendationSignals(hiddenTrackUris = setOf("hidden")),
            favourites = emptySet(),
        )

        assertEquals(listOf("plain"), result.map { it.track.contentUri })
    }

    @Test
    fun `the same artist outranks an artist merely in the queue`() {
        val same = track(artist = "Asake", uri = "same")
        val queued = track(artist = "Rema", uri = "queued")
        val stranger = track(artist = "Nobody", uri = "stranger")

        val result = recommendTracks(
            tracks = listOf(stranger, queued, same),
            seed = SuggestionSeed(artist = "Asake", queueArtists = listOf("Rema")),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals(listOf("same", "queued", "stranger"), result.map { it.track.contentUri })
    }

    @Test
    fun `artist matching ignores case`() {
        val result = recommendTracks(
            tracks = listOf(track(artist = "ASAKE", uri = "shouty")),
            seed = SuggestionSeed(artist = "asake"),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals("same artist", result.single().reason)
    }

    @Test
    fun `a blank seed artist does not match tracks with no artist`() {
        // Both sides lowercase to "", and an equality test alone would call every
        // untagged track a perfect match for every other untagged track.
        val untagged = track(artist = "", uri = "untagged", durationMs = outsideSongLength)

        val result = recommendTracks(
            tracks = listOf(untagged),
            seed = SuggestionSeed(artist = ""),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals("library discovery", result.single().reason)
    }

    @Test
    fun `liking a suggestion counts for more than starring the track`() {
        val liked = track(uri = "liked")
        val starred = track(uri = "starred")

        val result = recommendTracks(
            tracks = listOf(starred, liked),
            seed = SuggestionSeed(),
            signals = RecommendationSignals(likedTrackUris = setOf("liked")),
            favourites = setOf("starred"),
        )

        assertEquals(listOf("liked", "starred"), result.map { it.track.contentUri })
    }

    @Test
    fun `skipping a track repeatedly pushes it below everything else`() {
        val skipped = track(uri = "skipped")
        val plain = track(uri = "plain")

        val result = recommendTracks(
            tracks = listOf(skipped, plain),
            seed = SuggestionSeed(),
            signals = RecommendationSignals(trackSkipCounts = mapOf("skipped" to 3)),
            favourites = emptySet(),
        )

        assertEquals(listOf("plain", "skipped"), result.map { it.track.contentUri })
        assertTrue(scoreOf(result, "skipped") < 0)
        assertEquals("you often skip this", result.last().reason)
    }

    @Test
    fun `a skipped track is not floored back up to the discovery score`() {
        // The floor is `score == 0`, not `score <= 0`. A track pushed negative has
        // matched something, and rescuing it would erase the only negative signal.
        val result = recommendTracks(
            tracks = listOf(track(uri = "skipped", durationMs = outsideSongLength)),
            seed = SuggestionSeed(),
            signals = RecommendationSignals(trackSkipCounts = mapOf("skipped" to 1)),
            favourites = emptySet(),
        )

        assertEquals(-9, result.single().score)
    }

    @Test
    fun `enough favour outweighs the skips`() {
        val result = recommendTracks(
            tracks = listOf(track(artist = "Asake", uri = "conflicted")),
            seed = SuggestionSeed(artist = "Asake"),
            signals = RecommendationSignals(trackSkipCounts = mapOf("conflicted" to 2)),
            favourites = emptySet(),
        )

        assertTrue(scoreOf(result, "conflicted") > 0)
        // The reason is the strongest match, not the complaint.
        assertEquals("same artist", result.single().reason)
    }

    @Test
    fun `play counts stop counting past their cap`() {
        val heavy = track(artist = "Heavy", uri = "heavy")
        val heavier = track(artist = "Heavier", uri = "heavier")

        val result = recommendTracks(
            tracks = listOf(heavy, heavier),
            seed = SuggestionSeed(),
            signals = RecommendationSignals(
                artistPlayCounts = mapOf("heavy" to 20, "heavier" to 9_000),
            ),
            favourites = emptySet(),
        )

        assertEquals(scoreOf(result, "heavy"), scoreOf(result, "heavier"))
    }

    @Test
    fun `replays lift a track without inventing a reason for it`() {
        val result = recommendTracks(
            tracks = listOf(track(uri = "replayed")),
            seed = SuggestionSeed(),
            signals = RecommendationSignals(trackPlayCounts = mapOf("replayed" to 4)),
            favourites = emptySet(),
        )

        assertTrue(scoreOf(result, "replayed") > 1)
        assertEquals("good match for your queue", result.single().reason)
    }

    @Test
    fun `a long first word links two titles`() {
        val result = recommendTracks(
            tracks = listOf(track(title = "Peace of Mind", uri = "peace")),
            seed = SuggestionSeed(title = "Peace Be Still"),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals("title similarity", result.single().reason)
    }

    @Test
    fun `a short first word links nothing`() {
        // "The" would otherwise tie together every title starting with it.
        val result = recommendTracks(
            tracks = listOf(track(title = "The Other Song", uri = "other", durationMs = outsideSongLength)),
            seed = SuggestionSeed(title = "The One"),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals("library discovery", result.single().reason)
    }

    @Test
    fun `a library the app knows nothing about still suggests something`() {
        val tracks = (1..5).map { track(uri = "t$it", durationMs = outsideSongLength) }

        val result = recommendTracks(
            tracks = tracks,
            seed = SuggestionSeed(),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals(5, result.size)
        assertTrue(result.all { it.reason == "library discovery" })
        assertTrue(result.all { it.score == 1 })
    }

    @Test
    fun `song-length tracks edge in ahead of an interlude and a mix`() {
        val interlude = track(durationMs = 149_999L, uri = "interlude")
        val song = track(durationMs = 150_000L, uri = "song")
        val mix = track(durationMs = 360_001L, uri = "mix")

        val result = recommendTracks(
            tracks = listOf(interlude, mix, song),
            seed = SuggestionSeed(),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals("song", result.first().track.contentUri)
        assertTrue(scoreOf(result, "song") > scoreOf(result, "interlude"))
        assertTrue(scoreOf(result, "song") > scoreOf(result, "mix"))
    }

    @Test
    fun `refresh walks down the same ranking rather than scrambling it`() {
        val tracks = (1..10).map { track(uri = "t$it") }
        val first = uris(recommendTracks(tracks, seed(), signals(), noFavourites))
        val second = uris(recommendTracks(tracks, seed(), signals(), noFavourites, rotation = 1))

        // Rotated by a stride, not reshuffled: the tail is the head that came off.
        assertEquals(first.drop(5) + first.take(5), second)
    }

    @Test
    fun `one refresh replaces everything on screen`() {
        // The point of the stride. A shift of one would leave most of a screenful
        // where it was, and reads as the button doing nothing.
        val tracks = (1..10).map { track(uri = "t$it") }
        val first = uris(recommendTracks(tracks, seed(), signals(), noFavourites))
        val second = uris(recommendTracks(tracks, seed(), signals(), noFavourites, rotation = 1))

        val screenful = 4
        assertTrue(first.take(screenful).intersect(second.take(screenful).toSet()).isEmpty())
    }

    @Test
    fun `refreshing all the way round comes back to where it started`() {
        val tracks = (1..10).map { track(uri = "t$it") }
        val first = uris(recommendTracks(tracks, seed(), signals(), noFavourites))
        val wrapped = uris(recommendTracks(tracks, seed(), signals(), noFavourites, rotation = 2))

        assertEquals(first, wrapped)
    }

    @Test
    fun `refreshing a short list stays inside it`() {
        // The stride is wider than this list, so the shift has to wrap rather than
        // run off the end.
        val tracks = (1..3).map { track(uri = "t$it") }
        val first = uris(recommendTracks(tracks, seed(), signals(), noFavourites))
        val second = uris(recommendTracks(tracks, seed(), signals(), noFavourites, rotation = 1))

        assertEquals(first.toSet(), second.toSet())
        assertEquals(3, second.size)
    }

    @Test
    fun `a refresh count large enough to overflow an Int still rotates`() {
        val tracks = (1..10).map { track(uri = "t$it") }
        val result = recommendTracks(
            tracks, seed(), signals(), noFavourites, rotation = Int.MAX_VALUE,
        )

        assertEquals(10, result.size)
    }

    @Test
    fun `the list is capped`() {
        val tracks = (1..200).map { track(uri = "t$it") }

        val result = recommendTracks(
            tracks = tracks,
            seed = SuggestionSeed(),
            signals = RecommendationSignals(),
            favourites = emptySet(),
        )

        assertEquals(60, result.size)
    }
}
