// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbLoopTest {

    private val track = "content://media/1"
    private val other = "content://media/2"

    @Test
    fun `nothing is looping to begin with`() {
        val loop = AbLoop()

        assertFalse(loop.state.value.isActive)
        assertNull(loop.state.value.startMs)
    }

    @Test
    fun `the first mark sets the start and waits for the end`() {
        val loop = AbLoop()

        loop.mark(10_000L, track)

        assertEquals(10_000L, loop.state.value.startMs)
        assertTrue(loop.state.value.isWaitingForEnd)
        assertFalse(loop.state.value.isActive)
    }

    @Test
    fun `the second mark completes the loop`() {
        val loop = AbLoop()

        loop.mark(10_000L, track)
        loop.mark(20_000L, track)

        assertTrue(loop.state.value.isActive)
        assertEquals(20_000L, loop.state.value.endMs)
    }

    /** The same control cycles, so there is no separate reset to go looking for. */
    @Test
    fun `a third mark clears the loop`() {
        val loop = AbLoop()

        loop.mark(10_000L, track)
        loop.mark(20_000L, track)
        loop.mark(30_000L, track)

        assertFalse(loop.state.value.isActive)
        assertNull(loop.state.value.startMs)
    }

    /**
     * Someone scrubbing back to catch a phrase they have passed marks before A.
     * Taking it as the new start beats refusing it, and refusing it in silence is
     * worse still.
     */
    @Test
    fun `marking before the start moves the start rather than failing`() {
        val loop = AbLoop()

        loop.mark(30_000L, track)
        loop.mark(10_000L, track)

        assertEquals(10_000L, loop.state.value.startMs)
        assertNull(loop.state.value.endMs)
        assertTrue(loop.state.value.isWaitingForEnd)
    }

    /** A loop shorter than the ear can follow is a mis-tap. */
    @Test
    fun `a loop too short to hear is not accepted as an end`() {
        val loop = AbLoop()

        loop.mark(10_000L, track)
        loop.mark(10_100L, track)

        assertFalse(loop.state.value.isActive)
        assertEquals(10_100L, loop.state.value.startMs)
    }

    @Test
    fun `a loop of exactly the minimum length is allowed`() {
        val loop = AbLoop()

        loop.mark(10_000L, track)
        loop.mark(10_000L + AbLoopState.MINIMUM_LENGTH_MS + 1, track)

        assertTrue(loop.state.value.isActive)
    }

    // ---- passing the end ----------------------------------------------------

    @Test
    fun `playback past the end is detected`() {
        val loop = AbLoop()
        loop.mark(10_000L, track)
        loop.mark(20_000L, track)
        val state = loop.state.value

        assertFalse(state.hasPassedEnd(19_999L))
        assertTrue(state.hasPassedEnd(20_000L))
        assertTrue(state.hasPassedEnd(25_000L))
    }

    /**
     * A half-set loop must not jump anywhere. Sending someone back to A while they
     * are still choosing B makes B impossible to set.
     */
    @Test
    fun `a half-set loop never reports passing its end`() {
        val loop = AbLoop()
        loop.mark(10_000L, track)

        assertFalse(loop.state.value.hasPassedEnd(Long.MAX_VALUE))
    }

    // ---- track changes ------------------------------------------------------

    /** Positions in one track mean nothing in another. */
    @Test
    fun `changing track clears the loop`() {
        val loop = AbLoop()
        loop.mark(10_000L, track)
        loop.mark(20_000L, track)

        loop.clearIfTrackChanged(other)

        assertFalse(loop.state.value.isActive)
    }

    @Test
    fun `staying on the same track keeps the loop`() {
        val loop = AbLoop()
        loop.mark(10_000L, track)
        loop.mark(20_000L, track)

        loop.clearIfTrackChanged(track)

        assertTrue(loop.state.value.isActive)
    }

    /** Marking against a different track starts over rather than mixing positions. */
    @Test
    fun `marking on a new track does not inherit the old points`() {
        val loop = AbLoop()
        loop.mark(10_000L, track)

        loop.mark(5_000L, other)

        assertEquals(5_000L, loop.state.value.startMs)
        assertEquals(other, loop.state.value.trackUri)
    }
}
