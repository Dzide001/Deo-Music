// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebUrlTest {

    @Test
    fun `keeps an explicit https scheme`() {
        assertEquals("https://m.youtube.com", normalizeWebUrl("https://m.youtube.com"))
    }

    @Test
    fun `keeps an explicit http scheme`() {
        assertEquals("http://example.com", normalizeWebUrl("http://example.com"))
    }

    @Test
    fun `defaults a missing scheme to https`() {
        assertEquals("https://m.youtube.com", normalizeWebUrl("m.youtube.com"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("https://example.com", normalizeWebUrl("  example.com  "))
    }

    @Test
    fun `returns null for blank input`() {
        assertNull(normalizeWebUrl(""))
        assertNull(normalizeWebUrl("   "))
    }
}
