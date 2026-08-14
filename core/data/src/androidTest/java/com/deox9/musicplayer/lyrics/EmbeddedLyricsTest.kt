// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.lyrics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reading lyrics out of a real file, with eAlvaTag doing the parsing.
 *
 * The parser has its own unit tests; what those cannot show is whether the tag is
 * found at all — FieldKey.LYRICS maps to the USLT frame, and a file whose lyrics
 * live somewhere else would read as having none while every unit test stayed green.
 *
 * The fixture carries a genuine USLT frame, and its last line has two timestamps,
 * which is the case that was broken until the parser was rewritten.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddedLyricsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var fixture: File

    @Before
    fun copyFixture() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        fixture = File(context.cacheDir, "lyrics-fixture.mp3")
        assets.open("lyrics-fixture.mp3").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
    }

    @Test
    fun lyricsAreFoundInTheFilesOwnTag() = runBlocking {
        val lyrics = EmbeddedLyrics().read(fixture.absolutePath)

        assertNotNull("no lyrics read from a file that has a USLT frame", lyrics)
        assertEquals("embedded", lyrics!!.source)
        assertTrue("plain text is empty", lyrics.plainLyrics.isNotBlank())
    }

    /** LRC text in the plain lyrics frame is the common case, not the exception. */
    @Test
    fun timestampsInTheTagBecomeSyncedLines() = runBlocking {
        val lyrics = EmbeddedLyrics().read(fixture.absolutePath)!!

        assertEquals(
            listOf(1_000L, 3_500L, 5_000L, 80_000L),
            lyrics.syncedLines.map { it.timeMs },
        )
        assertEquals("First line here", lyrics.syncedLines.first().text)
    }

    /**
     * The bug the parser was rewritten for: one line carrying two timestamps is a
     * repeat, and the greedy version swallowed the second as part of the lyric.
     */
    @Test
    fun aLineWithTwoTimestampsBecomesTwoEntriesWithTheSameWords() = runBlocking {
        val lyrics = EmbeddedLyrics().read(fixture.absolutePath)!!

        val repeats = lyrics.syncedLines.filter { it.text == "Repeated chorus" }
        assertEquals(2, repeats.size)
        assertEquals(listOf(5_000L, 80_000L), repeats.map { it.timeMs })
    }

    /** With timings, the plain text is the words alone — no timestamps left in it. */
    @Test
    fun thePlainTextCarriesNoTimestamps() = runBlocking {
        val lyrics = EmbeddedLyrics().read(fixture.absolutePath)!!

        assertTrue(lyrics.plainLyrics, "[" !in lyrics.plainLyrics)
        assertTrue(lyrics.plainLyrics, "First line here" in lyrics.plainLyrics)
    }

    @Test
    fun aFileWithNoLyricsYieldsNothing() = runBlocking {
        val bare = File(context.cacheDir, "no-lyrics.mp3")
        bare.writeBytes(ByteArray(2048))

        assertNull(EmbeddedLyrics().read(bare.absolutePath))
        assertNull(EmbeddedLyrics().read("/nowhere.mp3"))
        assertNull(EmbeddedLyrics().read(null))
    }
}
