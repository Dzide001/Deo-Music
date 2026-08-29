// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowLayoutTest {

    @Test
    fun `width classes land on the Material breakpoints`() {
        assertEquals(WidthClass.Compact, widthClassFor(360))
        assertEquals(WidthClass.Compact, widthClassFor(599))
        assertEquals(WidthClass.Medium, widthClassFor(600))
        assertEquals(WidthClass.Medium, widthClassFor(839))
        assertEquals(WidthClass.Expanded, widthClassFor(840))
        assertEquals(WidthClass.Expanded, widthClassFor(1280))
    }

    @Test
    fun `the rail replaces the bottom bar from Medium up`() {
        assertEquals(NavigationStyle.BottomBar, navigationStyleFor(WidthClass.Compact))
        assertEquals(NavigationStyle.Rail, navigationStyleFor(WidthClass.Medium))
        assertEquals(NavigationStyle.Rail, navigationStyleFor(WidthClass.Expanded))
    }

    /**
     * The two thresholds are deliberately different. Splitting a Medium window gives
     * each pane less width than a phone has, so the rail arrives one step earlier than
     * the second pane.
     */
    @Test
    fun `two panes need Expanded, one step later than the rail`() {
        assertEquals(PaneLayout.Single, paneLayoutFor(WidthClass.Compact))
        assertEquals(PaneLayout.Single, paneLayoutFor(WidthClass.Medium))
        assertEquals(PaneLayout.TwoPane, paneLayoutFor(WidthClass.Expanded))

        assertEquals(NavigationStyle.Rail, navigationStyleFor(WidthClass.Medium))
    }

    /** A phone in landscape is Compact and still cannot stack cover over controls. */
    @Test
    fun `the player goes side by side on a short landscape window`() {
        assertTrue(playerUsesSideBySide(widthDp = 800, heightDp = 360))
        assertTrue(playerUsesSideBySide(widthDp = 640, heightDp = 400))
    }

    @Test
    fun `the player stays stacked when there is height to stack in`() {
        assertFalse("phone portrait", playerUsesSideBySide(widthDp = 360, heightDp = 800))
        assertFalse("tablet portrait", playerUsesSideBySide(widthDp = 800, heightDp = 1280))
        // Wide and tall: a large tablet in landscape has room for the stacked layout,
        // and the cover gets to be enormous.
        assertFalse("tablet landscape", playerUsesSideBySide(widthDp = 1280, heightDp = 800))
    }

    /** Square-ish windows must not flip layout on a pixel of rotation. */
    @Test
    fun `a square window stays stacked`() {
        assertFalse(playerUsesSideBySide(widthDp = 500, heightDp = 500))
    }
}
