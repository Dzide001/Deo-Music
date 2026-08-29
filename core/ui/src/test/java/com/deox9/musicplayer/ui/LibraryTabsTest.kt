// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTabsTest {

    @Test
    fun `everything is shown by default, in declaration order`() {
        val prefs = LibraryTabPreferences()

        assertEquals(LibraryTab.entries, prefs.visible)
    }

    @Test
    fun `hiding a tab removes it from what is drawn`() {
        val prefs = LibraryTabPreferences().toggleHidden(LibraryTab.Genres)

        assertTrue(LibraryTab.Genres !in prefs.visible)
        assertEquals(LibraryTab.entries.size - 1, prefs.visible.size)
    }

    /** Hiding then showing should put it back where it was, not at the end. */
    @Test
    fun `showing a hidden tab again restores its position`() {
        val prefs = LibraryTabPreferences()
            .toggleHidden(LibraryTab.Albums)
            .toggleHidden(LibraryTab.Albums)

        assertEquals(1, prefs.visible.indexOf(LibraryTab.Albums))
    }

    /** An empty strip would leave the library blank with no way back. */
    @Test
    fun `the last visible tab cannot be hidden`() {
        var prefs = LibraryTabPreferences()
        LibraryTab.entries.forEach { prefs = prefs.toggleHidden(it) }

        assertTrue(prefs.visible.isNotEmpty())
    }

    @Test
    fun `moving a tab changes its position`() {
        val prefs = LibraryTabPreferences().move(LibraryTab.Favourites, -1)

        assertEquals(LibraryTab.Favourites, prefs.resolvedOrder[LibraryTab.entries.size - 2])
    }

    @Test
    fun `moving past either end is clamped rather than wrapping`() {
        val first = LibraryTabPreferences().move(LibraryTab.Songs, -5)
        val last = LibraryTabPreferences().move(LibraryTab.Favourites, 5)

        assertEquals(LibraryTab.Songs, first.resolvedOrder.first())
        assertEquals(LibraryTab.Favourites, last.resolvedOrder.last())
    }

    @Test
    fun `moving keeps every tab exactly once`() {
        val prefs = LibraryTabPreferences().move(LibraryTab.Genres, -3).move(LibraryTab.Songs, 4)

        assertEquals(LibraryTab.entries.toSet(), prefs.resolvedOrder.toSet())
        assertEquals(LibraryTab.entries.size, prefs.resolvedOrder.size)
    }

    /**
     * A stored order from an older version will not mention a tab added since, and
     * that tab has to appear rather than vanish for anyone who reordered theirs.
     */
    @Test
    fun `a tab missing from a stored order is appended rather than lost`() {
        val partial = LibraryTabPreferences(order = listOf(LibraryTab.Albums, LibraryTab.Songs))

        assertEquals(LibraryTab.entries.size, partial.resolvedOrder.size)
        assertEquals(listOf(LibraryTab.Albums, LibraryTab.Songs), partial.resolvedOrder.take(2))
    }

    @Test
    fun `a duplicated entry in a stored order is not drawn twice`() {
        val dupes = LibraryTabPreferences(order = listOf(LibraryTab.Songs, LibraryTab.Songs))

        assertEquals(LibraryTab.entries.size, dupes.resolvedOrder.size)
    }
}
