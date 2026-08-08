// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun `formats sub-minute durations`() {
        assertEquals("0:07", formatDuration(7_000))
    }

    @Test
    fun `pads seconds to two digits`() {
        assertEquals("4:05", formatDuration(245_000))
    }

    @Test
    fun `formats a typical track`() {
        assertEquals("4:27", formatDuration(267_000))
    }

    /**
     * The previous implementation had no hour handling, so a four-hour file rendered
     * as "240:00". The roadmap calls out 4-hour files as a deliberate test case.
     */
    @Test
    fun `rolls over into hours`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("4:00:00", formatDuration(14_400_000))
        assertEquals("1:12:15", formatDuration(4_335_000))
    }

    @Test
    fun `treats zero and negative durations as zero`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-1))
    }
}
