// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reads genuinely tagged files rather than mocks.
 *
 * The fixtures are one-second sine tones written by ffmpeg and metaflac, carrying the
 * fields MediaStore cannot give us. Mocking the tag library here would only prove that
 * the mock returns what it was told to.
 */
class EAlvaTagReaderTest {

    private val reader = EAlvaTagReader()

    private fun fixture(name: String): String {
        val url = checkNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }
        return File(url.toURI()).absolutePath
    }

    @Test
    fun `reads vorbis comments from a flac`() {
        val tags = reader.read(fixture("flac_tagged.flac"))

        assertEquals("Various Artists", tags.albumArtist)
        assertEquals(2, tags.discNumber)
        assertEquals(5, tags.trackNumber)
        assertEquals("J.S. Bach", tags.composer)
        assertEquals(true, tags.isCompilation)
    }

    /** The whole reason this pass exists: MediaStore never exposes ReplayGain. */
    @Test
    fun `reads replaygain from a flac`() {
        val tags = reader.read(fixture("flac_tagged.flac"))

        assertNotNull("track gain should be read", tags.replayGainTrackDb)
        assertEquals(-6.48f, tags.replayGainTrackDb!!, 0.001f)
        assertEquals(0.987654f, tags.replayGainTrackPeak!!, 0.000001f)
        assertEquals(-7.20f, tags.replayGainAlbumDb!!, 0.001f)
        assertEquals(1.012345f, tags.replayGainAlbumPeak!!, 0.000001f)
    }

    @Test
    fun `reads id3 tags from an mp3`() {
        val tags = reader.read(fixture("mp3_tagged.mp3"))

        assertEquals("Various Artists", tags.albumArtist)
        assertEquals(2, tags.discNumber)
        assertEquals(5, tags.trackNumber)
        assertEquals("J.S. Bach", tags.composer)
    }

    @Test
    fun `returns empty rather than throwing for a missing file`() {
        assertTrue(reader.read("/does/not/exist.mp3").isEmpty)
        assertTrue(reader.read(null).isEmpty)
    }

    /** A corrupt file must cost one track, not abort the whole scan. */
    @Test
    fun `returns empty rather than throwing for a non-audio file`() {
        val notAudio = File.createTempFile("junk", ".mp3").apply {
            writeText("this is definitely not an mp3")
            deleteOnExit()
        }

        assertTrue(reader.read(notAudio.absolutePath).isEmpty)
    }

    @Test
    fun `file tags overlay a scanned track without erasing what is already known`() {
        val scanned = ScannedTrack(
            mediaUri = "uri://1",
            sourceId = 1,
            albumSourceId = 1,
            title = "Title",
            artist = "Performer",
            albumArtist = null,
            album = "Album",
            genre = "Jazz",
            folderPath = "Music",
            filePath = null,
            trackNumber = null,
            discNumber = null,
            year = 1975,
            durationMs = 1000,
            mimeType = "audio/flac",
            sizeBytes = 1,
            bitrateKbps = null,
            dateAddedMs = 0,
            dateModifiedMs = 0,
            isCompilation = false,
        )

        val merged = scanned.mergedWith(reader.read(fixture("flac_tagged.flac")))

        // Filled from the file.
        assertEquals("Various Artists", merged.albumArtist)
        assertEquals(2, merged.discNumber)
        assertEquals(-6.48f, merged.replayGainTrackDb!!, 0.001f)
        // Untouched, because the file carries no competing value.
        assertEquals("Performer", merged.artist)
        assertEquals("Jazz", merged.genre)
        assertEquals(1975, merged.year)
    }

    @Test
    fun `an empty tag set leaves the scanned track alone`() {
        val scanned = ScannedTrack(
            mediaUri = "uri://1", sourceId = null, albumSourceId = null, title = "Title",
            artist = "Performer", albumArtist = "Album Artist", album = "Album", genre = null,
            folderPath = null, filePath = null, trackNumber = 3, discNumber = 1, year = null,
            durationMs = 1, mimeType = null, sizeBytes = 0, bitrateKbps = null,
            dateAddedMs = 0, dateModifiedMs = 0, isCompilation = false,
        )

        assertEquals(scanned, scanned.mergedWith(TrackTags.EMPTY))
        assertNull(scanned.mergedWith(TrackTags.EMPTY).replayGainTrackDb)
    }
}
