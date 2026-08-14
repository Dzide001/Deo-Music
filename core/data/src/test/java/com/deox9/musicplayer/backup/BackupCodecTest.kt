// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs against the real org.json, pulled onto the unit-test classpath deliberately.
 *
 * android.jar ships stubs that throw "not mocked" for every method, so the first run
 * of these failed ten out of ten on JSONObject.put rather than on anything this file
 * is about — worth knowing before writing the next test that parses something.
 */
class BackupCodecTest {

    private val sample = BackupData(
        createdAtMs = 1_700_000_000_000L,
        appVersion = "1.2.3",
        playlists = listOf(
            BackupPlaylist(
                name = "Late night",
                tracks = listOf(
                    BackupTrackRef("/music/a.mp3", "Grade 1", "Stonebwoy", "Grade 1", 222_000L),
                    BackupTrackRef("/music/b.mp3", "Putuu", "Stonebwoy", "Anloga", 180_000L),
                ),
            ),
        ),
        favourites = listOf(BackupTrackRef("/music/a.mp3", "Grade 1", "Stonebwoy", "Grade 1", 222_000L)),
        playCounts = listOf(
            BackupPlayCount(
                track = BackupTrackRef("/music/a.mp3", "Grade 1", "Stonebwoy"),
                playCount = 17,
                lastPlayedAtMs = 1_699_000_000_000L,
            ),
        ),
        settings = mapOf("eq_enabled" to "true", "crossfade_duration_ms" to "363"),
    )

    @Test
    fun `a backup survives a round trip intact`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample)).getOrThrow()

        assertEquals(sample, decoded)
    }

    @Test
    fun `an empty backup round-trips`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(BackupData())).getOrThrow()

        assertEquals(BackupData(), decoded)
    }

    /** It should be openable and checkable by the person it is about. */
    @Test
    fun `the file is readable text`() {
        val raw = BackupCodec.encode(sample)

        assertTrue(raw, raw.contains("Late night"))
        assertTrue(raw, raw.contains("\n"))
    }

    // ---- refusing what it should refuse -------------------------------------

    /**
     * The rule that matters. Reading the parts of a newer format you recognise and
     * ignoring the rest silently drops whatever was added: the user is told their
     * playlists came back and does not find out until later that something else
     * did not.
     */
    @Test
    fun `a newer format is refused rather than partly read`() {
        val raw = BackupCodec.encode(sample).replace("\"version\": 1", "\"version\": 99")

        val failure = BackupCodec.decode(raw).exceptionOrNull()

        assertTrue(failure is BackupException)
        val reason = (failure as BackupException).failure
        assertTrue(reason.toString(), reason is BackupCodec.Failure.TooNew)
        assertTrue(reason.message, reason.message.contains("newer version"))
    }

    @Test
    fun `something that is not json at all is refused`() {
        val failure = BackupCodec.decode("not a backup").exceptionOrNull()

        assertTrue((failure as BackupException).failure is BackupCodec.Failure.NotABackup)
    }

    /** Valid JSON that is some other file entirely must not be read as an empty backup. */
    @Test
    fun `json without a version is not treated as a backup`() {
        val failure = BackupCodec.decode("""{"playlists":[]}""").exceptionOrNull()

        assertTrue((failure as BackupException).failure is BackupCodec.Failure.NotABackup)
    }

    @Test
    fun `the refusal says what to do about it in plain words`() {
        val failure = BackupCodec.decode("""{"version":99}""").exceptionOrNull()

        val message = (failure as BackupException).failure.message
        assertTrue(message, message.contains("99") && message.contains("1"))
    }

    // ---- damaged files ------------------------------------------------------

    /**
     * A backup is the last copy of something by definition, so one bad entry loses
     * that entry rather than the file. That is the opposite of the version rule,
     * and deliberately so: a short read is visible, a wrong read is not.
     */
    @Test
    fun `one malformed entry does not lose the rest of the file`() {
        val raw = """
            {
              "version": 1,
              "playlists": [
                {"name":"Good","tracks":[{"path":"/a.mp3","title":"T","artist":"A"}]},
                "this is not an object",
                {"name":"Also good","tracks":[]}
              ]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(raw).getOrThrow()

        assertEquals(listOf("Good", "Also good"), decoded.playlists.map { it.name })
    }

    @Test
    fun `missing optional sections decode as empty rather than failing`() {
        val decoded = BackupCodec.decode("""{"version":1}""").getOrThrow()

        assertTrue(decoded.playlists.isEmpty())
        assertTrue(decoded.favourites.isEmpty())
        assertTrue(decoded.playCounts.isEmpty())
        assertTrue(decoded.settings.isEmpty())
    }

    @Test
    fun `settings survive as opaque pairs so a new key needs no code here`() {
        val raw = """{"version":1,"settings":{"something_added_later":"yes"}}"""

        val decoded = BackupCodec.decode(raw).getOrThrow()

        assertEquals("yes", decoded.settings["something_added_later"])
    }
}
