// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueExpansionTest {

    private val album = listOf("a", "b", "c")

    /** A queue restored at launch is not something the listener just chose. */
    @Test
    fun `the first queue it sees does not open the player`() {
        assertFalse(QueueExpansion().shouldExpand(album))
    }

    @Test
    fun `replacing the queue opens the player`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)

        assertTrue(expansion.shouldExpand(listOf("x", "y")))
    }

    /** Play-next and add-to-queue: they are still browsing, leave them there. */
    @Test
    fun `appending to the queue does not open the player`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)

        assertFalse(expansion.shouldExpand(album + "d"))
    }

    @Test
    fun `an unchanged queue does not open the player`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)

        assertFalse(expansion.shouldExpand(album))
    }

    /**
     * Same length, different tracks. Comparing only sizes would call this an append
     * and leave the listener looking at the wrong screen.
     */
    @Test
    fun `a replacement of the same length still opens the player`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)

        assertTrue(expansion.shouldExpand(listOf("x", "y", "z")))
    }

    /** A shorter queue cannot be an append, whatever its contents. */
    @Test
    fun `removing tracks is treated as a replacement`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)

        assertTrue(expansion.shouldExpand(listOf("a")))
    }

    @Test
    fun `emptying the queue does not open the player and starts over`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)

        assertFalse(expansion.shouldExpand(emptyList()))
        // Having reset, the next queue is a first sighting again.
        assertFalse(expansion.shouldExpand(listOf("x")))
    }

    @Test
    fun `suppression stops a replacement opening the player`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)
        expansion.suppress(true)

        assertFalse(expansion.shouldExpand(listOf("x", "y")))
    }

    /** Suppression must not be permanent, or the player never opens again. */
    @Test
    fun `lifting suppression restores the behaviour`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)
        expansion.suppress(true)
        expansion.shouldExpand(listOf("x", "y"))
        expansion.suppress(false)

        assertTrue(expansion.shouldExpand(listOf("p", "q", "r")))
    }

    /** A suppressed change still updates the record, or the next one misreads it. */
    @Test
    fun `a suppressed change is still remembered`() {
        val expansion = QueueExpansion()
        expansion.shouldExpand(album)
        expansion.suppress(true)
        expansion.shouldExpand(listOf("x", "y"))
        expansion.suppress(false)

        assertFalse(expansion.shouldExpand(listOf("x", "y", "z")))
    }
}
