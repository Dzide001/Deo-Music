// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcOffsetTest {

    @Test
    fun `a file with no offset tag is unshifted`() {
        assertEquals(0L, LrcParser.offsetMsIn("[00:10.00]Line"))
    }

    /**
     * The convention, and the reason the sign is flipped on the way in: in an LRC
     * file a positive offset means the lyrics should appear *earlier*, so it becomes
     * a negative shift of the timestamps.
     */
    @Test
    fun `a positive offset tag brings the lyrics forward`() {
        assertEquals(-500L, LrcParser.offsetMsIn("[offset:+500]\n[00:10.00]Line"))
    }

    @Test
    fun `a negative offset tag delays them`() {
        assertEquals(300L, LrcParser.offsetMsIn("[offset:-300]\n[00:10.00]Line"))
    }

    @Test
    fun `the tag is read wherever it sits and whatever its case`() {
        assertEquals(-250L, LrcParser.offsetMsIn("[00:01.00]First\n[OFFSET: 250]\n[00:02.00]Second"))
    }

    @Test
    fun `a malformed offset tag is ignored rather than guessed at`() {
        assertEquals(0L, LrcParser.offsetMsIn("[offset:soon]\n[00:10.00]Line"))
    }

    @Test
    fun `parsing applies the file's own offset`() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00]Line")

        assertEquals(1, lines.size)
        assertEquals(9_500L, lines.single().timeMs)
    }

    @Test
    fun `shifting moves every line`() {
        val lines = listOf(SyncedLyricLine(1_000L, "a"), SyncedLyricLine(2_000L, "b"))
        val shifted = LrcParser.shiftBy(lines, 250L)

        assertEquals(listOf(1_250L, 2_250L), shifted.map { it.timeMs })
    }

    @Test
    fun `a line pushed before the start clamps rather than disappearing`() {
        // Losing the first line silently would look like the lyrics were incomplete.
        val lines = listOf(SyncedLyricLine(100L, "a"), SyncedLyricLine(5_000L, "b"))
        val shifted = LrcParser.shiftBy(lines, -1_000L)

        assertEquals(listOf(0L, 4_000L), shifted.map { it.timeMs })
    }

    @Test
    fun `a zero shift returns the lines untouched`() {
        val lines = listOf(SyncedLyricLine(1_000L, "a"))
        assertEquals(lines, LrcParser.shiftBy(lines, 0L))
    }
}
