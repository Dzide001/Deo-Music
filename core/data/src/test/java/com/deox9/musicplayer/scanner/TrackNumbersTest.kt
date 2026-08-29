// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackNumbersTest {

    @Test
    fun `plain track numbers have no disc component`() {
        val position = TrackNumbers.fromLegacyTrackColumn(5)

        assertNull(position.disc)
        assertEquals(5, position.track)
    }

    /** MediaStore packs the legacy column as disc * 1000 + track. */
    @Test
    fun `unpacks disc and track from the legacy column`() {
        val position = TrackNumbers.fromLegacyTrackColumn(2005)

        assertEquals(2, position.disc)
        assertEquals(5, position.track)
    }

    @Test
    fun `unpacks a high track number on a later disc`() {
        val position = TrackNumbers.fromLegacyTrackColumn(3120)

        assertEquals(3, position.disc)
        assertEquals(120, position.track)
    }

    @Test
    fun `treats a disc marker with no track as disc only`() {
        val position = TrackNumbers.fromLegacyTrackColumn(2000)

        assertEquals(2, position.disc)
        assertNull(position.track)
    }

    @Test
    fun `treats missing and non-positive values as absent`() {
        listOf(null, 0, -1).forEach { value ->
            val position = TrackNumbers.fromLegacyTrackColumn(value)
            assertNull(position.disc)
            assertNull(position.track)
        }
    }

    @Test
    fun `parses position strings with and without a total`() {
        assertEquals(5, TrackNumbers.parsePositionString("5"))
        assertEquals(5, TrackNumbers.parsePositionString("5/12"))
        assertEquals(5, TrackNumbers.parsePositionString(" 5 / 12 "))
    }

    @Test
    fun `rejects unparseable position strings`() {
        listOf(null, "", "  ", "abc", "0", "-1").forEach {
            assertNull("expected null for ${it.orEmpty()}", TrackNumbers.parsePositionString(it))
        }
    }
}
