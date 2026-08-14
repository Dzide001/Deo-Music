// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `a timestamped line yields its time and text`() {
        val parsed = LrcParser.parse("[00:12.50]Hello there")

        assertEquals(1, parsed.size)
        assertEquals(12_500L, parsed.single().timeMs)
        assertEquals("Hello there", parsed.single().text)
    }

    /**
     * The fraction is not always hundredths. Reading three digits as hundredths, or
     * one as thousandths, puts a line up to nine tenths of a second out — which on
     * a lyric is the difference between in time and visibly wrong.
     */
    @Test
    fun `fractions are read in the unit they are written in`() {
        assertEquals(12_500L, LrcParser.parse("[00:12.5]x").single().timeMs)
        assertEquals(12_050L, LrcParser.parse("[00:12.05]x").single().timeMs)
        assertEquals(12_005L, LrcParser.parse("[00:12.005]x").single().timeMs)
    }

    @Test
    fun `a line with no fraction is on the second`() {
        assertEquals(72_000L, LrcParser.parse("[01:12]x").single().timeMs)
    }

    /** Some writers use a colon before the fraction rather than a dot. */
    @Test
    fun `a colon separator is accepted as well as a dot`() {
        assertEquals(12_500L, LrcParser.parse("[00:12:50]x").single().timeMs)
    }

    /** LRC repeats a chorus by giving one line several timestamps. */
    @Test
    fun `a line with several timestamps becomes several entries`() {
        val parsed = LrcParser.parse("[00:10.00][01:20.00]Chorus")

        assertEquals(2, parsed.size)
        assertEquals(listOf(10_000L, 80_000L), parsed.map { it.timeMs })
        assertTrue(parsed.all { it.text == "Chorus" })
    }

    @Test
    fun `lines come back in time order however they were written`() {
        val parsed = LrcParser.parse("[00:30.00]third\n[00:10.00]first\n[00:20.00]second")

        assertEquals(listOf("first", "second", "third"), parsed.map { it.text })
    }

    /** Metadata tags carry no lyric and must not become empty lines. */
    @Test
    fun `header tags are not treated as lyrics`() {
        val parsed = LrcParser.parse("[ar:Stonebwoy]\n[ti:Grade 1]\n[00:10.00]Real line")

        assertEquals(listOf("Real line"), parsed.map { it.text })
    }

    @Test
    fun `plain text with no timestamps yields nothing`() {
        assertTrue(LrcParser.parse("Just some words\nAnd more").isEmpty())
        assertTrue(LrcParser.parse("").isEmpty())
    }

    @Test
    fun `an empty line at a timestamp is skipped`() {
        assertTrue(LrcParser.parse("[00:10.00]   ").isEmpty())
    }
}
