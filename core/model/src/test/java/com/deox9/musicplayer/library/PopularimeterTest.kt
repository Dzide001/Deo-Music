// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PopularimeterTest {

    @Test
    fun `writes the values every other player reads`() {
        // Windows Media Player's scale, followed by MediaMonkey, foobar2000, MusicBee
        // and most of the Android players people migrate from. A tidier linear
        // mapping would be misread everywhere.
        assertEquals(0, Popularimeter.toPopm(0))
        assertEquals(1, Popularimeter.toPopm(1))
        assertEquals(64, Popularimeter.toPopm(2))
        assertEquals(128, Popularimeter.toPopm(3))
        assertEquals(196, Popularimeter.toPopm(4))
        assertEquals(255, Popularimeter.toPopm(5))
    }

    @Test
    fun `every star count survives a round trip`() {
        (0..5).forEach { stars ->
            assertEquals(stars, Popularimeter.toStars(Popularimeter.toPopm(stars)))
        }
    }

    @Test
    fun `a byte from software using another scale lands in the nearest band`() {
        // Some players spread ratings across the whole range rather than using the
        // five conventional values.
        assertEquals(1, Popularimeter.toStars(20))
        assertEquals(2, Popularimeter.toStars(70))
        assertEquals(3, Popularimeter.toStars(110))
        assertEquals(4, Popularimeter.toStars(180))
        assertEquals(5, Popularimeter.toStars(240))
    }

    @Test
    fun `the band edges fall between the conventional values`() {
        assertEquals(1, Popularimeter.toStars(31))
        assertEquals(2, Popularimeter.toStars(32))
        assertEquals(2, Popularimeter.toStars(95))
        assertEquals(3, Popularimeter.toStars(96))
        assertEquals(3, Popularimeter.toStars(159))
        assertEquals(4, Popularimeter.toStars(160))
        assertEquals(4, Popularimeter.toStars(223))
        assertEquals(5, Popularimeter.toStars(224))
    }

    @Test
    fun `zero is unrated rather than zero stars`() {
        assertEquals(0, Popularimeter.toStars(0))
        assertNull(Popularimeter.format(0))
    }

    @Test
    fun `a star count out of range is clamped rather than trusted`() {
        assertEquals(255, Popularimeter.toPopm(9))
        assertEquals(0, Popularimeter.toPopm(-3))
    }

    @Test
    fun `a byte beyond a byte still reads as five`() {
        assertEquals(5, Popularimeter.toStars(300))
    }

    @Test
    fun `nothing in the tag means no rating`() {
        assertNull(Popularimeter.parse(null))
        assertNull(Popularimeter.parse(""))
        assertNull(Popularimeter.parse("   "))
        assertNull(Popularimeter.parse("great"))
        assertNull(Popularimeter.parse("-4"))
    }

    @Test
    fun `a tagger writing stars directly is taken at its word`() {
        (0..5).forEach { assertEquals(it, Popularimeter.parse(it.toString())) }
    }

    @Test
    fun `a POPM byte is read as POPM`() {
        assertEquals(1, Popularimeter.parse("1"))
        assertEquals(2, Popularimeter.parse("64"))
        assertEquals(3, Popularimeter.parse("128"))
        assertEquals(4, Popularimeter.parse("196"))
        assertEquals(5, Popularimeter.parse("255"))
    }

    @Test
    fun `a Vorbis or MP4 rating out of a hundred is read as a percentage`() {
        // A FLAC rated three stars stores 60 where an MP3 stores 128.
        assertEquals(1, Popularimeter.parse("20"))
        assertEquals(2, Popularimeter.parse("40"))
        assertEquals(3, Popularimeter.parse("60"))
        assertEquals(4, Popularimeter.parse("80"))
        assertEquals(5, Popularimeter.parse("100"))
    }

    @Test
    fun `a percentage between the marks rounds to the nearest star`() {
        assertEquals(3, Popularimeter.parse("55"))
        assertEquals(4, Popularimeter.parse("70"))
    }

    @Test
    fun `the POPM reading wins where the two scales collide`() {
        // 64 is two stars in POPM and would be three out of a hundred. This app
        // writes POPM, and misreading its own files back would be the worse failure.
        assertEquals(2, Popularimeter.parse("64"))
        assertEquals(3, Popularimeter.parse("128"))
    }

    @Test
    fun `what gets written back is what other software expects`() {
        assertEquals("1", Popularimeter.format(1))
        assertEquals("128", Popularimeter.format(3))
        assertEquals("255", Popularimeter.format(5))
    }
}
