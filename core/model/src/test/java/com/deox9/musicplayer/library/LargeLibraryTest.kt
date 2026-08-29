// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * The library computations against thirty thousand tracks.
 *
 * The real test device holds about 150, which is enough to be sure a screen renders
 * and useless for noticing that something is quadratic. Every function here runs on
 * the whole library, and several run again on every playback state change — so a
 * mistake that is invisible at 150 tracks is a frozen screen at 30,000.
 *
 * These measure how the work *scales*, not how long it takes. A wall-clock budget
 * was the first attempt and it does not work: measured on this machine the real
 * functions take 3-24ms and a deliberately quadratic pass over the same library
 * takes 355ms, so any budget loose enough to survive a slow CI runner is also loose
 * enough to wave the quadratic through. Verified — the first version of this test
 * passed with the quadratic in place, which is the failure mode that makes a timing
 * test worse than none.
 *
 * So each function runs at n and at 2n and the ratio is checked instead. Linear work
 * roughly doubles; quadratic work roughly quadruples; and a ratio is not affected by
 * the machine being slow, only by it being inconsistent — which repeated runs and a
 * generous threshold cover.
 */
class LargeLibraryTest {

    private companion object {
        const val LIBRARY_SIZE = 30_000

        /**
         * Doubling the input may cost up to this much more time.
         *
         * Linear work lands near 2. Quadratic lands near 4. Three sits between them
         * with room for the noise of a shared CI machine, and is low enough that the
         * 355ms quadratic measured here fails it comfortably.
         */
        const val MAX_SCALING_FACTOR = 3.0

        const val WARMUP_RUNS = 2
        const val MEASURED_RUNS = 3
    }

    /** A deterministic library, so a failure is reproducible rather than a mood. */
    private val library: List<LocalTrack> = run {
        val random = Random(seed = 20260817)
        val artists = List(500) { "Artist $it" }
        val albums = List(2_000) { "Album $it" }
        List(LIBRARY_SIZE) { i ->
            LocalTrack(
                id = i.toLong(),
                title = "Track $i",
                artist = artists[random.nextInt(artists.size)],
                album = albums[random.nextInt(albums.size)],
                durationMs = (60_000..400_000).random(random).toLong(),
                contentUri = "content://media/external/audio/media/$i",
                dateAddedMs = 1_600_000_000_000L + i,
            )
        }
    }

    private val playCounts: Map<String, Int> =
        library.take(5_000).associate { it.contentUri to (it.id.toInt() % 40) + 1 }

    /**
     * Runs [work] over half the library and then all of it, and compares.
     *
     * Best-of-three at each size, after a warm-up pass. The JIT compiles on the way
     * through, so a single first run measures the interpreter as much as the code,
     * and the ratio of two such measurements is meaningless.
     */
    private fun assertScalesLinearly(name: String, work: (List<LocalTrack>) -> Unit) {
        val half = library.take(LIBRARY_SIZE / 2)

        repeat(WARMUP_RUNS) {
            work(half)
            work(library)
        }

        val smallMs = bestOf { work(half) }
        val largeMs = bestOf { work(library) }

        // Below a millisecond the ratio is measuring the clock, not the code.
        val ratio = largeMs.toDouble() / smallMs.coerceAtLeast(1L)
        assertTrue(
            "$name scaled by ${"%.1f".format(ratio)}x when the library doubled " +
                "(${smallMs}ms at ${half.size}, ${largeMs}ms at $LIBRARY_SIZE) — " +
                "linear work lands near 2x",
            ratio < MAX_SCALING_FACTOR,
        )
    }

    private fun bestOf(block: () -> Unit): Long =
        (1..MEASURED_RUNS).minOf { measureTimeMillis(block) }

    @Test
    fun `ranking recommendations stays linear`() {
        // The worst case for this one: it runs again on every track change, so a
        // regression here shows up as the Suggested tab stuttering during playback.
        assertScalesLinearly("recommendTracks") { tracks ->
            val result = recommendTracks(
                tracks = tracks,
                seed = SuggestionSeed(
                    uri = library.first().contentUri,
                    artist = library.first().artist,
                    title = library.first().title,
                    queueArtists = library.take(20).map { it.artist },
                ),
                signals = RecommendationSignals(trackPlayCounts = playCounts),
                favourites = library.take(100).map { it.contentUri }.toSet(),
            )
            // The cap is what keeps the screen usable; without it this returns 30,000
            // rows into a LazyColumn.
            assertEquals(60, result.size)
        }
    }

    @Test
    fun `generating playlists stays linear`() {
        assertScalesLinearly("autoPlaylists") { tracks ->
            val result = autoPlaylists(tracks, playCounts)
            assertEquals(2, result.size)
            assertTrue(result.all { it.trackCount == 50 })
        }
    }

    @Test
    fun `listening statistics stay linear`() {
        assertScalesLinearly("listeningStats") { tracks ->
            val stats = listeningStats(tracks, RecommendationSignals(trackPlayCounts = playCounts))
            assertEquals(10, stats.topTracks.size)
        }
    }

    @Test
    fun `sorting the whole library stays linear`() {
        // What the Songs tab does on every sort change, and the one place where a
        // comparator that lowercases inside the comparison rather than before it
        // turns an n log n sort into something much worse.
        assertScalesLinearly("sort by title") { tracks ->
            val sorted = tracks.sortedBy { it.title.lowercase() }
            assertEquals(tracks.size, sorted.size)
        }
    }

    @Test
    fun `filtering the whole library stays linear`() {
        assertScalesLinearly("filter") { tracks ->
            val query = "track 1"
            val matches = tracks.filter {
                it.title.lowercase().contains(query) ||
                    it.artist.lowercase().contains(query) ||
                    it.album.lowercase().contains(query)
            }
            assertTrue("a substring search should match many titles", matches.isNotEmpty())
        }
    }

    @Test
    fun `a library this size still produces a sane ranking`() {
        // Not a timing test. At this size it is worth confirming the result is still
        // ordered and still respects the exclusions, because a cap hides most
        // ordering mistakes behind a short list.
        val result = recommendTracks(
            tracks = library,
            seed = SuggestionSeed(uri = library.first().contentUri),
            signals = RecommendationSignals(
                trackPlayCounts = playCounts,
                hiddenTrackUris = library.take(200).map { it.contentUri }.toSet(),
            ),
            favourites = emptySet(),
        )

        assertTrue("scores must be descending", result.zipWithNext().all { it.first.score >= it.second.score })
        assertTrue("hidden tracks must not appear", result.none { it.track.id < 200 })
    }
}
