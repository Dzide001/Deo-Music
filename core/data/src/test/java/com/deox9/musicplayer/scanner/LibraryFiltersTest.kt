// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFiltersTest {

    private val whatsapp = "/storage/emulated/0/WhatsApp"

    @Test
    fun `a hidden folder hides itself`() {
        assertTrue(LibraryFilters.isHidden(whatsapp, setOf(whatsapp)))
    }

    /** Hiding a folder and still getting its subfolders is not hiding it. */
    @Test
    fun `a hidden folder hides everything under it`() {
        assertTrue(LibraryFilters.isHidden("$whatsapp/Media/Audio", setOf(whatsapp)))
    }

    /**
     * The separator is what stops a prefix match running away: /music/live and
     * /music/livestreams share a prefix and nothing else.
     */
    @Test
    fun `a folder that merely starts with the same letters is not hidden`() {
        assertFalse(LibraryFilters.isHidden("/music/livestreams", setOf("/music/live")))
        assertTrue(LibraryFilters.isHidden("/music/live/set1", setOf("/music/live")))
    }

    /** The same directory arrives with different casing from different sources. */
    @Test
    fun `matching ignores case`() {
        assertTrue(LibraryFilters.isHidden("/Storage/Emulated/0/whatsapp", setOf(whatsapp)))
    }

    @Test
    fun `a trailing slash on either side does not matter`() {
        assertTrue(LibraryFilters.isHidden("$whatsapp/", setOf("$whatsapp/")))
        assertTrue(LibraryFilters.isHidden(whatsapp, setOf("$whatsapp/")))
    }

    @Test
    fun `nothing is hidden when the list is empty`() {
        assertFalse(LibraryFilters.isHidden(whatsapp, emptySet()))
    }

    @Test
    fun `a track with no folder is not hidden`() {
        assertFalse(LibraryFilters.isHidden(null, setOf(whatsapp)))
        assertFalse(LibraryFilters.isHidden("  ", setOf(whatsapp)))
    }

    /** A blank entry must not hide the entire library. */
    @Test
    fun `a blank entry in the list hides nothing`() {
        assertFalse(LibraryFilters.isHidden("/music/a", setOf("", "   ")))
    }

    // ---- duration -----------------------------------------------------------

    @Test
    fun `zero means no duration filter`() {
        assertTrue(LibraryFilters.isLongEnough(1_000L, 0L))
        assertTrue(LibraryFilters.isLongEnough(0L, 0L))
    }

    @Test
    fun `a track shorter than the threshold is excluded`() {
        assertFalse(LibraryFilters.isLongEnough(15_000L, 60_000L))
        assertTrue(LibraryFilters.isLongEnough(60_000L, 60_000L))
        assertTrue(LibraryFilters.isLongEnough(61_000L, 60_000L))
    }

    // ---- both together ------------------------------------------------------

    private fun track(path: String, durationMs: Long) = ScannedTrack(
        mediaUri = "content://$path",
        sourceId = null,
        albumSourceId = null,
        title = path.substringAfterLast('/'),
        artist = null,
        albumArtist = null,
        album = null,
        genre = null,
        folderPath = path.substringBeforeLast('/'),
        filePath = path,
        trackNumber = null,
        discNumber = null,
        year = null,
        durationMs = durationMs,
        mimeType = "audio/mpeg",
        sizeBytes = 1L,
        bitrateKbps = 128,
        dateAddedMs = 0L,
        dateModifiedMs = 0L,
        isCompilation = false,
    )

    @Test
    fun `filtering removes hidden folders and short tracks and keeps the rest`() {
        val tracks = listOf(
            track("/music/keep.mp3", 200_000L),
            track("$whatsapp/Media/note.opus", 200_000L),
            track("/music/blip.mp3", 3_000L),
        )

        val kept = LibraryFilters.apply(tracks, setOf(whatsapp), 60_000L)

        assertTrue(kept.map { it.title } == listOf("keep.mp3"))
    }

    @Test
    fun `filtering with nothing configured keeps everything`() {
        val tracks = listOf(track("/music/a.mp3", 1_000L), track("$whatsapp/b.opus", 1L))

        assertTrue(LibraryFilters.apply(tracks, emptySet(), 0L).size == 2)
    }
}
