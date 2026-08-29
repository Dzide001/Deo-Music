// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleModesTest {

    private data class Track(
        val id: String,
        override val groupAlbum: String,
        override val groupFolder: String,
    ) : Shuffleable

    /** Two albums of three, interleaved in the source order to make grouping visible. */
    private val library = listOf(
        Track("a1", "Album A", "/music/a"),
        Track("b1", "Album B", "/music/b"),
        Track("a2", "Album A", "/music/a"),
        Track("b2", "Album B", "/music/b"),
        Track("a3", "Album A", "/music/a"),
        Track("b3", "Album B", "/music/b"),
    )

    private fun seeded() = Random(20260814)

    @Test
    fun `off leaves the order exactly as it was`() {
        assertEquals(library, shuffleQueue(library, ShuffleMode.Off, seeded()))
    }

    @Test
    fun `shuffling tracks keeps every track and changes the order`() {
        val shuffled = shuffleQueue(library, ShuffleMode.Tracks, seeded())

        assertEquals(library.toSet(), shuffled.toSet())
        assertEquals(library.size, shuffled.size)
        assertNotEquals(library, shuffled)
    }

    /**
     * The point of the grouped modes: a record someone chose to hear in order stays
     * in order, and only the records are shuffled.
     */
    @Test
    fun `shuffling albums keeps each album whole and in its own order`() {
        val shuffled = shuffleQueue(library, ShuffleMode.Albums, seeded())

        val albumRuns = shuffled.map { it.groupAlbum }
        // Each album appears as one unbroken run, so exactly two runs in total.
        val runs = albumRuns.fold(mutableListOf<String>()) { acc, album ->
            if (acc.lastOrNull() != album) acc.add(album)
            acc
        }
        assertEquals(2, runs.size)
        assertEquals(listOf("a1", "a2", "a3"), shuffled.filter { it.groupAlbum == "Album A" }.map { it.id })
        assertEquals(listOf("b1", "b2", "b3"), shuffled.filter { it.groupAlbum == "Album B" }.map { it.id })
    }

    @Test
    fun `shuffling folders groups by folder rather than album`() {
        val mixed = listOf(
            Track("x", "Album A", "/music/one"),
            Track("y", "Album B", "/music/one"),
            Track("z", "Album A", "/music/two"),
        )

        val shuffled = shuffleQueue(mixed, ShuffleMode.Folders, seeded())

        val runs = shuffled.map { it.groupFolder }.fold(mutableListOf<String>()) { acc, f ->
            if (acc.lastOrNull() != f) acc.add(f)
            acc
        }
        assertEquals(2, runs.size)
    }

    /** The same seed must give the same order, or a reshuffle is not reproducible. */
    @Test
    fun `the same seed gives the same order`() {
        assertEquals(
            shuffleQueue(library, ShuffleMode.Albums, Random(7)),
            shuffleQueue(library, ShuffleMode.Albums, Random(7)),
        )
    }

    @Test
    fun `an empty or single-track queue survives every mode`() {
        ShuffleMode.entries.forEach { mode ->
            assertTrue(shuffleQueue(emptyList<Track>(), mode, seeded()).isEmpty())
            assertEquals(1, shuffleQueue(library.take(1), mode, seeded()).size)
        }
    }

    // ---- playing everything before repeating --------------------------------

    /**
     * True random repeats a track before playing others at all, which reads as the
     * shuffle being broken. A pass plays everything once.
     */
    @Test
    fun `a pass plays every track exactly once`() {
        val sequence = ShuffleSequence(library, ShuffleMode.Tracks, seeded())

        val played = (1..library.size).mapNotNull { sequence.next() }

        assertEquals(library.size, played.size)
        assertEquals(library.toSet(), played.toSet())
    }

    @Test
    fun `the sequence keeps going past the end of a pass`() {
        val sequence = ShuffleSequence(library, ShuffleMode.Tracks, seeded())

        val played = (1..library.size * 3).mapNotNull { sequence.next() }

        assertEquals(library.size * 3, played.size)
    }

    /** The one repeat anyone notices is the track that just played, playing again. */
    @Test
    fun `a new pass does not begin with the track that just ended`() {
        repeat(20) { seed ->
            val sequence = ShuffleSequence(library, ShuffleMode.Tracks, Random(seed))
            val firstPass = (1..library.size).mapNotNull { sequence.next() }
            val next = sequence.next()

            assertNotEquals("seed $seed", firstPass.last(), next)
        }
    }

    @Test
    fun `remaining counts down through a pass`() {
        val sequence = ShuffleSequence(library, ShuffleMode.Tracks, seeded())

        assertEquals(library.size, sequence.remaining)
        sequence.next()
        assertEquals(library.size - 1, sequence.remaining)
    }

    @Test
    fun `an empty queue yields nothing rather than failing`() {
        assertNull(ShuffleSequence(emptyList<Track>(), ShuffleMode.Tracks, seeded()).next())
    }

    /** With two tracks half of all orders start with the last one; it must not spin. */
    @Test
    fun `a two-track queue does not hang looking for a different order`() {
        val two = library.take(2)
        val sequence = ShuffleSequence(two, ShuffleMode.Tracks, seeded())

        val played = (1..10).mapNotNull { sequence.next() }

        assertEquals(10, played.size)
    }
}
