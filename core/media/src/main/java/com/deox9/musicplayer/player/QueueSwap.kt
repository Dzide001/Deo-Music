// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

/**
 * Index arithmetic for exchanging two queue entries.
 *
 * Extracted from the service so the semantics are unit-testable without a Player.
 * The previous implementation removed one item and added two, which duplicated a
 * track and grew the queue by one on every swap.
 */
internal object QueueSwap {

    /**
     * Returns the `(from, to)` move pairs that exchange [fromIndex] and [toIndex].
     *
     * Moving `from` to `to` displaces the item previously at `to` by one position;
     * the second move returns it to where `from` started. Queue size is unchanged.
     */
    fun movesFor(fromIndex: Int, toIndex: Int): List<Pair<Int, Int>> {
        if (fromIndex == toIndex) return emptyList()

        val displacedIndex = if (toIndex > fromIndex) toIndex - 1 else toIndex + 1
        return listOf(
            fromIndex to toIndex,
            displacedIndex to fromIndex,
        )
    }
}
