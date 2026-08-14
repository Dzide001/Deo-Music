// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFormatsTest {

    private val entries = listOf(
        PlaylistFileEntry("/music/a.mp3", 225, "Stonebwoy - Grade 1"),
        PlaylistFileEntry("/music/b.mp3", 180, "Stonebwoy - Putuu"),
    )

    // ---- writing ------------------------------------------------------------

    @Test
    fun `an exported playlist has the header and one path per track`() {
        val text = PlaylistFormats.writeM3u(entries)
        val lines = text.trim().lines()

        assertEquals("#EXTM3U", lines.first())
        assertTrue(text, "/music/a.mp3" in lines)
        assertTrue(text, "/music/b.mp3" in lines)
    }

    @Test
    fun `each track carries its duration and name`() {
        val text = PlaylistFormats.writeM3u(entries)

        assertTrue(text, "#EXTINF:225,Stonebwoy - Grade 1" in text.lines())
    }

    /** Relative paths survive the whole library moving, which is the common case. */
    @Test
    fun `paths can be written relative to the playlist's own folder`() {
        val text = PlaylistFormats.writeM3u(entries, relativeTo = "/music")

        assertTrue(text, "a.mp3" in text.lines())
        assertTrue(text, "/music/a.mp3" !in text.lines())
    }

    @Test
    fun `a track outside the playlist's folder keeps its absolute path`() {
        val outside = listOf(PlaylistFileEntry("/elsewhere/c.mp3", 100, "C"))

        val text = PlaylistFormats.writeM3u(outside, relativeTo = "/music")

        assertTrue(text, "/elsewhere/c.mp3" in text.lines())
    }

    @Test
    fun `a track with nothing known writes just its path`() {
        val text = PlaylistFormats.writeM3u(listOf(PlaylistFileEntry("/music/a.mp3")))

        assertTrue(text, "#EXTINF" !in text)
    }

    // ---- round trip ---------------------------------------------------------

    /** The exit criterion: what is written must read back as what went in. */
    @Test
    fun `a playlist round-trips through the writer and reader`() {
        val text = PlaylistFormats.writeM3u(entries)

        assertEquals(entries, PlaylistFormats.parse(text))
    }

    @Test
    fun `a relative export round-trips when read from the same folder`() {
        val text = PlaylistFormats.writeM3u(entries, relativeTo = "/music")

        assertEquals(entries, PlaylistFormats.parse(text, baseDirectory = "/music"))
    }

    // ---- reading M3U --------------------------------------------------------

    @Test
    fun `a bare list of paths is a valid playlist`() {
        val parsed = PlaylistFormats.parseM3u("/music/a.mp3\n/music/b.mp3\n")

        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), parsed.map { it.path })
    }

    /** Files written on Windows carry carriage returns that would join the path. */
    @Test
    fun `windows line endings do not become part of the path`() {
        val parsed = PlaylistFormats.parseM3u("#EXTM3U\r\n/music/a.mp3\r\n")

        assertEquals("/music/a.mp3", parsed.single().path)
    }

    @Test
    fun `blank lines and unknown directives are ignored`() {
        val parsed = PlaylistFormats.parseM3u("#EXTM3U\n\n#PLAYLIST:Mine\n\n/music/a.mp3\n")

        assertEquals(1, parsed.size)
        assertEquals("/music/a.mp3", parsed.single().path)
    }

    /** A title may contain commas, so only the first one separates it from the duration. */
    @Test
    fun `a title containing a comma survives`() {
        val parsed = PlaylistFormats.parseM3u("#EXTINF:225,Artist - Hello, Goodbye\n/music/a.mp3\n")

        assertEquals("Artist - Hello, Goodbye", parsed.single().title)
        assertEquals(225, parsed.single().durationSeconds)
    }

    @Test
    fun `a relative path resolves against the playlist's folder`() {
        val parsed = PlaylistFormats.parseM3u("a.mp3\nsub/b.mp3\n", baseDirectory = "/music")

        assertEquals(listOf("/music/a.mp3", "/music/sub/b.mp3"), parsed.map { it.path })
    }

    /** A playlist may point at a stream, and a URL is not a relative path. */
    @Test
    fun `a url is left alone rather than resolved`() {
        val parsed = PlaylistFormats.parseM3u("https://example.com/s.mp3\n", baseDirectory = "/music")

        assertEquals("https://example.com/s.mp3", parsed.single().path)
    }

    @Test
    fun `a malformed duration does not lose the track`() {
        val parsed = PlaylistFormats.parseM3u("#EXTINF:not-a-number,Title\n/music/a.mp3\n")

        assertEquals("/music/a.mp3", parsed.single().path)
        assertEquals(-1, parsed.single().durationSeconds)
    }

    // ---- reading PLS --------------------------------------------------------

    @Test
    fun `a PLS playlist is recognised by its content rather than its name`() {
        val pls = """
            [playlist]
            NumberOfEntries=2
            File1=/music/a.mp3
            Title1=Grade 1
            Length1=225
            File2=/music/b.mp3
            Title2=Putuu
            Length2=180
        """.trimIndent()

        val parsed = PlaylistFormats.parse(pls)

        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), parsed.map { it.path })
        assertEquals("Grade 1", parsed.first().title)
        assertEquals(225, parsed.first().durationSeconds)
    }

    /** The numbers order the entries, not their position in the file. */
    @Test
    fun `PLS entries are ordered by their number not their line`() {
        val pls = "[playlist]\nFile2=/music/b.mp3\nFile1=/music/a.mp3\n"

        val parsed = PlaylistFormats.parse(pls)

        assertEquals(listOf("/music/a.mp3", "/music/b.mp3"), parsed.map { it.path })
    }

    @Test
    fun `PLS keys are matched whatever their case`() {
        val parsed = PlaylistFormats.parse("[Playlist]\nFILE1=/music/a.mp3\nTITLE1=X\n")

        assertEquals("/music/a.mp3", parsed.single().path)
        assertEquals("X", parsed.single().title)
    }

    @Test
    fun `an empty file yields no tracks rather than failing`() {
        assertTrue(PlaylistFormats.parse("").isEmpty())
        assertTrue(PlaylistFormats.parse("#EXTM3U\n").isEmpty())
    }
}
