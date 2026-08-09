// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTagsTest {

    private fun assertGain(expected: Float, raw: String) {
        assertEquals("parsing $raw", expected, ReplayGainTags.parseGainDb(raw)!!, 0.001f)
    }

    @Test
    fun `parses the common encoder spellings`() {
        assertGain(-6.48f, "-6.48 dB")
        assertGain(-6.48f, "-6.48dB")
        assertGain(-6.48f, "-6.48")
        assertGain(-6.48f, "  -6.48 dB  ")
    }

    @Test
    fun `parses an explicit plus sign`() {
        assertGain(2.30f, "+2.30 dB")
        assertGain(2.30f, "+2.30")
    }

    @Test
    fun `parses a lowercase unit`() {
        assertGain(-3.5f, "-3.5 db")
        assertGain(-3.5f, "-3.5 DB")
    }

    /** 0 dB means measured and needs no change; null means never measured. */
    @Test
    fun `zero is a real measurement and not treated as absent`() {
        assertEquals(0f, ReplayGainTags.parseGainDb("0.00 dB")!!, 0.001f)
    }

    @Test
    fun `returns null for absent or unparseable gain`() {
        listOf(null, "", "   ", "dB", "abc", "--3", "N/A").forEach {
            assertNull("expected null for ${it ?: "null"}", ReplayGainTags.parseGainDb(it))
        }
    }

    /** A tag reading -300 dB is corrupt; applying it would silence the track. */
    @Test
    fun `rejects implausible gain values`() {
        assertNull(ReplayGainTags.parseGainDb("-300 dB"))
        assertNull(ReplayGainTags.parseGainDb("999 dB"))
    }

    @Test
    fun `parses peak values`() {
        assertEquals(0.987654f, ReplayGainTags.parsePeak("0.987654")!!, 0.000001f)
        assertEquals(1.0f, ReplayGainTags.parsePeak("1.000000")!!, 0.000001f)
    }

    /** Material that clips after decoding legitimately peaks above 1.0. */
    @Test
    fun `keeps peaks above one rather than clamping`() {
        assertEquals(1.034f, ReplayGainTags.parsePeak("1.034")!!, 0.001f)
    }

    @Test
    fun `rejects negative and unparseable peaks`() {
        listOf(null, "", "-0.5", "loud").forEach {
            assertNull("expected null for ${it ?: "null"}", ReplayGainTags.parsePeak(it))
        }
    }

    @Test
    fun `exposes the keys a scanner should look for`() {
        assertEquals(
            listOf(
                "REPLAYGAIN_TRACK_GAIN",
                "REPLAYGAIN_TRACK_PEAK",
                "REPLAYGAIN_ALBUM_GAIN",
                "REPLAYGAIN_ALBUM_PEAK",
            ),
            ReplayGainTags.KEYS,
        )
    }
}
