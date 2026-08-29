// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for queue swapping.
 *
 * The original implementation removed one item and added two, so every swap
 * duplicated a track and grew the queue by one. These tests pin the invariants
 * that broke: the queue keeps its size, and exactly two elements exchange places.
 */
class QueueSwapTest {

    /** Applies the computed moves the same way Media3's moveMediaItem does. */
    private fun applySwap(queue: List<String>, fromIndex: Int, toIndex: Int): List<String> {
        val working = queue.toMutableList()
        QueueSwap.movesFor(fromIndex, toIndex).forEach { (from, to) ->
            val item = working.removeAt(from)
            working.add(to, item)
        }
        return working
    }

    @Test
    fun `swapping forward exchanges exactly the two items`() {
        val result = applySwap(listOf("A", "B", "C", "D"), fromIndex = 0, toIndex = 2)

        assertEquals(listOf("C", "B", "A", "D"), result)
    }

    @Test
    fun `swapping backward exchanges exactly the two items`() {
        val result = applySwap(listOf("A", "B", "C", "D"), fromIndex = 2, toIndex = 0)

        assertEquals(listOf("C", "B", "A", "D"), result)
    }

    @Test
    fun `swapping adjacent items exchanges them`() {
        val result = applySwap(listOf("A", "B", "C"), fromIndex = 0, toIndex = 1)

        assertEquals(listOf("B", "A", "C"), result)
    }

    @Test
    fun `swapping never changes queue size`() {
        val queue = listOf("A", "B", "C", "D", "E")

        for (from in queue.indices) {
            for (to in queue.indices) {
                val result = applySwap(queue, from, to)

                assertEquals(
                    "size changed swapping $from <-> $to",
                    queue.size,
                    result.size,
                )
                assertEquals(
                    "items duplicated or lost swapping $from <-> $to",
                    queue.toSet(),
                    result.toSet(),
                )
            }
        }
    }

    @Test
    fun `swapping an index with itself is a no-op`() {
        assertEquals(emptyList<Pair<Int, Int>>(), QueueSwap.movesFor(3, 3))
    }

    @Test
    fun `swapping twice returns the original order`() {
        val queue = listOf("A", "B", "C", "D")

        val once = applySwap(queue, fromIndex = 1, toIndex = 3)
        val twice = applySwap(once, fromIndex = 1, toIndex = 3)

        assertEquals(queue, twice)
    }
}
