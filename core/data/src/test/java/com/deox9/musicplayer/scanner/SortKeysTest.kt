// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class SortKeysTest {

    @Test
    fun `moves a leading article off the front`() {
        assertEquals("beatles", SortKeys.forTitle("The Beatles"))
        assertEquals("wall", SortKeys.forTitle("A Wall"))
        assertEquals("evening with", SortKeys.forTitle("An Evening With"))
    }

    @Test
    fun `only strips an article that is a whole word`() {
        assertEquals("theatre of tragedy", SortKeys.forTitle("Theatre of Tragedy"))
        assertEquals("answer", SortKeys.forTitle("Answer"))
    }

    /** "Ólafur" must sort with O, not after Z. */
    @Test
    fun `folds diacritics onto base letters`() {
        assertEquals("olafur arnalds", SortKeys.forTitle("Ólafur Arnalds"))
        assertEquals("bjork", SortKeys.forTitle("Björk"))
        assertEquals("sigur ros", SortKeys.forTitle("Sigur Rós"))
    }

    /** CJK has no case or diacritics to fold, and folding would destroy it. */
    @Test
    fun `leaves CJK intact`() {
        assertEquals("久石譲", SortKeys.forTitle("久石譲"))
        assertEquals("김광석", SortKeys.forTitle("김광석"))
    }

    @Test
    fun `collapses whitespace and trims`() {
        assertEquals("dark side", SortKeys.forTitle("  Dark    Side  "))
    }

    @Test
    fun `handles empty and blank input`() {
        assertEquals("", SortKeys.forTitle(""))
        assertEquals("", SortKeys.forTitle("   "))
    }

    @Test
    fun `sorts a mixed list the way a listener expects`() {
        val titles = listOf("The Zoo", "Ólafur", "Abbey Road", "A Night", "Björk")
        val sorted = titles.sortedBy { SortKeys.forTitle(it) }

        assertEquals(listOf("Abbey Road", "Björk", "A Night", "Ólafur", "The Zoo"), sorted)
    }
}
