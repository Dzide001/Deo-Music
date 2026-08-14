// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private fun candidate(
        path: String,
        title: String = "Grade 1",
        artist: String = "Stonebwoy",
        durationMs: Long = 222_000L,
        uri: String = "content://media/external/audio/media/1",
    ) = RestoreCandidate(
        contentUri = uri,
        path = path,
        title = title,
        artist = artist,
        album = "Grade 1",
        durationMs = durationMs,
    )

    // ---- resolving a track --------------------------------------------------

    /** Restoring onto the same phone, which is what most restores are. */
    @Test
    fun `an exact path wins`() {
        val ref = BackupTrackRef(path = "/storage/emulated/0/Music/a.mp3", title = "Grade 1", artist = "Stonebwoy")
        val candidates = listOf(
            candidate("/storage/emulated/0/Music/b.mp3", uri = "uri-b"),
            candidate("/storage/emulated/0/Music/a.mp3", uri = "uri-a"),
        )

        assertEquals("uri-a", resolveTrack(ref, candidates)?.contentUri)
    }

    /** The library moved to an SD card; the file is the same file. */
    @Test
    fun `a moved file is found by its name`() {
        val ref = BackupTrackRef(path = "/storage/emulated/0/Music/a.mp3", title = "Grade 1", artist = "Stonebwoy")
        val candidates = listOf(candidate("/storage/ABCD-1234/Music/a.mp3", uri = "uri-sd"))

        assertEquals("uri-sd", resolveTrack(ref, candidates)?.contentUri)
    }

    /**
     * Two files with the same name in different folders are a coin flip, and a wrong
     * track in a playlist is worse than a missing one because nothing signals it.
     */
    @Test
    fun `an ambiguous file name is not guessed at`() {
        val ref = BackupTrackRef(path = "/old/a.mp3", title = "Nope", artist = "Nobody")
        val candidates = listOf(
            candidate("/music/one/a.mp3", title = "X", artist = "Y", uri = "uri-1"),
            candidate("/music/two/a.mp3", title = "X", artist = "Y", uri = "uri-2"),
        )

        assertNull(resolveTrack(ref, candidates))
    }

    /** Re-encoded or renamed: only the recording identifies it now. */
    @Test
    fun `tags and duration find a renamed file`() {
        val ref = BackupTrackRef(
            path = "/gone/old-name.mp3",
            title = "Grade 1",
            artist = "Stonebwoy",
            durationMs = 222_000L,
        )
        val candidates = listOf(candidate("/music/renamed.mp3", durationMs = 222_400L, uri = "uri-r"))

        assertEquals("uri-r", resolveTrack(ref, candidates)?.contentUri)
    }

    /** A re-encode shifts the length by a frame or two; exactness would defeat the point. */
    @Test
    fun `a small duration difference still matches`() {
        val ref = BackupTrackRef(title = "Grade 1", artist = "Stonebwoy", durationMs = 222_000L)

        assertTrue(resolveTrack(ref, listOf(candidate("/a.mp3", durationMs = 223_500L))) != null)
        assertTrue(resolveTrack(ref, listOf(candidate("/a.mp3", durationMs = 220_500L))) != null)
    }

    /**
     * Title and artist alone match every live version, remix and cover in a large
     * library, so a clearly different length is a different recording.
     */
    @Test
    fun `a different recording of the same song does not match`() {
        val ref = BackupTrackRef(title = "Grade 1", artist = "Stonebwoy", durationMs = 222_000L)
        val live = candidate("/live.mp3", durationMs = 401_000L)

        assertNull(resolveTrack(ref, listOf(live)))
    }

    @Test
    fun `matching by tags ignores case`() {
        val ref = BackupTrackRef(title = "grade 1", artist = "STONEBWOY", durationMs = 222_000L)

        assertTrue(resolveTrack(ref, listOf(candidate("/a.mp3"))) != null)
    }

    @Test
    fun `a track that is simply not here resolves to nothing`() {
        val ref = BackupTrackRef(path = "/gone.mp3", title = "Missing", artist = "Nobody")

        assertNull(resolveTrack(ref, listOf(candidate("/other.mp3"))))
    }

    @Test
    fun `a reference with no identity at all is rejected`() {
        assertFalse(BackupTrackRef().hasAnyIdentity)
        assertNull(resolveTrack(BackupTrackRef(), listOf(candidate("/a.mp3"))))
    }

    @Test
    fun `an empty library matches nothing rather than failing`() {
        val ref = BackupTrackRef(path = "/a.mp3", title = "Grade 1", artist = "Stonebwoy")

        assertNull(resolveTrack(ref, emptyList()))
    }

    /** A single tag match with no duration recorded is accepted; there is nothing better. */
    @Test
    fun `one unambiguous tag match is taken when no duration was stored`() {
        val ref = BackupTrackRef(title = "Grade 1", artist = "Stonebwoy", durationMs = 0L)

        assertEquals("uri-only", resolveTrack(ref, listOf(candidate("/a.mp3", uri = "uri-only")))?.contentUri)
    }

    @Test
    fun `the file name is read off the path`() {
        assertEquals("a.mp3", BackupTrackRef(path = "/storage/Music/a.mp3").fileName)
        assertEquals("a.mp3", BackupTrackRef(path = "a.mp3").fileName)
    }

    // ---- the report ---------------------------------------------------------

    @Test
    fun `a clean restore says what it restored`() {
        val report = RestoreReport(playlistsRestored = 3, favouritesMatched = 12)

        assertFalse(report.anythingMissing)
        assertEquals("3 playlists, 12 favourites", report.summary())
    }

    /**
     * Missing tracks are named rather than swallowed. A restore that quietly drops
     * half a playlist looks like a success until someone plays it.
     */
    @Test
    fun `missing tracks are reported`() {
        val report = RestoreReport(
            playlistsRestored = 2,
            favouritesMatched = 5,
            playlistTracksMissing = 4,
            favouritesMissing = 1,
        )

        assertTrue(report.anythingMissing)
        assertTrue(report.summary(), report.summary().contains("5 tracks not found"))
    }

    @Test
    fun `one missing track is not described as plural`() {
        val report = RestoreReport(playlistTracksMissing = 1)

        assertTrue(report.summary(), report.summary().contains("1 track not found"))
    }

    @Test
    fun `the format carries a version so an unknown one can be refused`() {
        assertEquals(1, BackupData.CURRENT_VERSION)
        assertEquals(BackupData.CURRENT_VERSION, BackupData().version)
    }
}
