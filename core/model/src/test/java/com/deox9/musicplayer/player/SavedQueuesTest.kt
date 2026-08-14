// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedQueuesTest {

    private fun queue(id: String, name: String = id, updatedAtMs: Long = 0L, size: Int = 3) =
        SavedQueue(
            id = id,
            name = name,
            tracks = (1..size).map { SavedQueueTrack("uri-$id-$it", "Track $it") },
            currentIndex = 0,
            updatedAtMs = updatedAtMs,
        )

    @Test
    fun `saving a queue makes it active when nothing else is`() {
        val queues = SavedQueues().save(queue("a"))

        assertEquals("a", queues.activeId)
        assertEquals(1, queues.queues.size)
    }

    @Test
    fun `saving the same id updates rather than duplicating`() {
        val queues = SavedQueues()
            .save(queue("a", name = "First", updatedAtMs = 1))
            .save(queue("a", name = "Renamed", updatedAtMs = 2))

        assertEquals(1, queues.queues.size)
        assertEquals("Renamed", queues.queues.single().name)
    }

    /** The switcher is read at a glance, so the one you want is near the top. */
    @Test
    fun `queues are listed newest first`() {
        val queues = SavedQueues()
            .save(queue("a", updatedAtMs = 1))
            .save(queue("b", updatedAtMs = 3))
            .save(queue("c", updatedAtMs = 2))

        assertEquals(listOf("b", "c", "a"), queues.queues.map { it.id })
    }

    @Test
    fun `the list is capped`() {
        val queues = (1..SavedQueues.MAX_QUEUES + 5).fold(SavedQueues()) { acc, n ->
            acc.save(queue("q$n", updatedAtMs = n.toLong()))
        }

        assertEquals(SavedQueues.MAX_QUEUES, queues.queues.size)
    }

    /**
     * Dropping the active queue off the end would leave activeId pointing at
     * nothing, which reads as "nothing playing" while audio carries on.
     */
    @Test
    fun `the active queue is never dropped by the cap`() {
        var queues = SavedQueues().save(queue("keep", updatedAtMs = 0)).activate("keep")
        repeat(SavedQueues.MAX_QUEUES + 3) { n ->
            queues = queues.save(queue("q$n", updatedAtMs = (n + 1).toLong()))
        }

        // The queue itself survives, not merely a valid pointer: the previous
        // version repointed activeId at a different queue and called that safe.
        assertEquals("keep", queues.activeId)
        assertTrue(queues.queues.any { it.id == "keep" })
        assertEquals(SavedQueues.MAX_QUEUES, queues.queues.size)
    }

    // ---- switching and removing ---------------------------------------------

    @Test
    fun `activating switches which queue is playing`() {
        val queues = SavedQueues().save(queue("a")).save(queue("b")).activate("a")

        assertEquals("a", queues.activeId)
    }

    @Test
    fun `activating an unknown queue changes nothing`() {
        val queues = SavedQueues().save(queue("a")).activate("nope")

        assertEquals("a", queues.activeId)
    }

    @Test
    fun `removing the active queue moves to another rather than leaving none`() {
        val queues = SavedQueues()
            .save(queue("a", updatedAtMs = 1))
            .save(queue("b", updatedAtMs = 2))
            .activate("b")
            .remove("b")

        assertEquals("a", queues.activeId)
        assertEquals(1, queues.queues.size)
    }

    @Test
    fun `removing the only queue leaves nothing active`() {
        val queues = SavedQueues().save(queue("a")).remove("a")

        assertNull(queues.activeId)
        assertTrue(queues.queues.isEmpty())
    }

    @Test
    fun `renaming keeps everything else`() {
        val queues = SavedQueues().save(queue("a", size = 4)).rename("a", "  Night  ")

        assertEquals("Night", queues.queues.single().name)
        assertEquals(4, queues.queues.single().tracks.size)
    }

    @Test
    fun `a blank rename is refused rather than blanking the name`() {
        val queues = SavedQueues().save(queue("a", name = "Real")).rename("a", "   ")

        assertEquals("Real", queues.queues.single().name)
    }

    // ---- position ------------------------------------------------------------

    /** The whole point: coming back to where you were, not to the beginning. */
    @Test
    fun `a queue remembers where it had reached`() {
        val saved = queue("a", size = 8).copy(currentIndex = 6, positionMs = 134_000L)

        val restored = SavedQueues().save(saved).active!!

        assertEquals(6, restored.safeIndex)
        assertEquals(134_000L, restored.positionMs)
        assertEquals("uri-a-7", restored.currentTrack?.uri)
    }

    /** A saved index can outlive the tracks it pointed at. */
    @Test
    fun `an index past the end is clamped rather than crashing`() {
        val shrunk = queue("a", size = 2).copy(currentIndex = 9)

        assertEquals(1, shrunk.safeIndex)
        assertEquals("uri-a-2", shrunk.currentTrack?.uri)
    }

    @Test
    fun `an empty queue has no current track and does not crash`() {
        val empty = SavedQueue(id = "a", name = "Empty")

        assertEquals(0, empty.safeIndex)
        assertNull(empty.currentTrack)
        assertTrue(empty.isEmpty)
    }

    // ---- naming --------------------------------------------------------------

    @Test
    fun `a suggested name counts up rather than repeating`() {
        val queues = SavedQueues()
            .save(queue("a", name = "Queue"))
            .save(queue("b", name = "Queue 2"))

        assertEquals("Queue 3", queues.suggestName())
    }

    @Test
    fun `the first suggestion is the plain name`() {
        assertEquals("Queue", SavedQueues().suggestName())
    }
}
