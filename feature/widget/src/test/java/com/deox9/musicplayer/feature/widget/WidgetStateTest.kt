// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.widget

import com.deox9.musicplayer.player.storage.PlaybackSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {

    private fun session(
        uri: String = "content://media/external/audio/media/1",
        title: String = "Grade 1",
        artist: String = "Stonebwoy",
        albumArtUri: String = "content://media/external/audio/albumart/9",
        isPlaying: Boolean = true,
    ) = PlaybackSessionEntity(
        uri = uri,
        title = title,
        artist = artist,
        positionMs = 1_000L,
        durationMs = 222_000L,
        isPlaying = isPlaying,
        queue = emptyList(),
        currentIndex = 0,
        updatedAtMs = 0L,
        albumArtUri = albumArtUri,
    )

    @Test
    fun `a session maps onto what the widget draws`() {
        val state = session().toWidgetState()

        assertEquals("Grade 1", state.title)
        assertEquals("Stonebwoy", state.artist)
        assertEquals("content://media/external/audio/albumart/9", state.albumArtUri)
        assertTrue(state.isPlaying)
        assertTrue(state.hasTrack)
    }

    @Test
    fun `no session at all is the empty state`() {
        assertEquals(WidgetState.Empty, null.toWidgetState())
        assertFalse(WidgetState.Empty.hasTrack)
    }

    /**
     * A stored row with a blank URI is not the same as no row: it used to render as a
     * playable-looking track called "Unknown title" with dead transport controls.
     */
    @Test
    fun `a session with no uri is treated as nothing playing`() {
        val state = session(uri = "").toWidgetState()

        assertEquals(WidgetState.Empty, state)
        assertFalse(state.hasTrack)
    }

    @Test
    fun `a blank artwork uri becomes null rather than an unloadable empty string`() {
        assertNull(session(albumArtUri = "").toWidgetState().albumArtUri)
    }

    @Test
    fun `a blank title falls back rather than drawing an empty row`() {
        assertEquals("Unknown title", session(title = "").toWidgetState().title)
    }

    /**
     * The command travels by name because a pinned widget outlives the install that
     * created it; an ordinal would turn every pinned Next into whatever now sits at
     * that position.
     */
    @Test
    fun `command names are stable identifiers`() {
        assertEquals(
            listOf("PlayPause", "Next", "Previous"),
            WidgetCommand.entries.map { it.name },
        )
    }
}
