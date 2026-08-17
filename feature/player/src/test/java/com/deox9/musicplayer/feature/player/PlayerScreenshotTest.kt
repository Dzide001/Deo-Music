// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.deox9.musicplayer.player.PlaybackState
import com.deox9.musicplayer.player.QueueEntry
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the player's pieces and compares them against committed images.
 *
 * These exist because several refactors landed in one session with nothing but me
 * driving the phone to catch a layout regression — and driving the phone missed a
 * crash until I read logcat. A screenshot test cannot say whether a screen looks
 * *good*, but it will never forget to look.
 *
 * That these composables can be rendered at all is the refactoring paying for
 * itself: each takes plain values and callbacks rather than reaching for a Hilt view
 * model, which is what lets them draw with no app around them.
 *
 * MiniPlayerBar is here because it was made coverable. It used to take
 * `viewModel: PlayerViewModel = hiltViewModel()` for two transport calls, which made
 * it look stateless while quietly needing a Hilt graph — it threw before drawing
 * anything. It now takes the two actions instead, and renders like the rest.
 *
 * Robolectric with NATIVE graphics rather than AGP's preview screenshot plugin. That
 * plugin is the tidier tool and it does not work here: on AGP 9.3.1 it registers its
 * tasks, compiles the previews, and then reports discovering no tests at all —
 * the same alpha-tooling lag that made baselineprofile 1.4.1 unusable on AGP 9.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayerScreenshotTest {

    private val accent = Color(0xFFFFC107)

    private val playingSession = PlaybackState(
        uri = "content://media/external/audio/media/1",
        title = "Peace Be Still",
        artist = "Sunmisola Agbebi",
        album = "Live at the Sanctuary",
        positionMs = 74_000L,
        durationMs = 245_000L,
        isPlaying = true,
        queue = listOf(QueueEntry("content://1", "Peace Be Still", "Sunmisola Agbebi")),
    )

    /**
     * Renders straight to a bitmap, with no Activity involved.
     *
     * The obvious route — createComposeRule — needs a launcher activity that no
     * library module has, and fails with "Unable to resolve activity for Intent".
     * Roborazzi can draw a composable on its own, which sidesteps the activity, the
     * test manifest and the whole scenario machinery.
     */
    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }

    @Test
    fun `rating row unrated`() {
        capture("rating-unrated") {
            StarRatingRow(rating = 0, enabled = true, accent = accent, writeRefused = false, onRate = {})
        }
    }

    @Test
    fun `rating row at four stars`() {
        capture("rating-four-stars") {
            StarRatingRow(rating = 4, enabled = true, accent = accent, writeRefused = false, onRate = {})
        }
    }

    /**
     * The scoped-storage case: the rating is kept, the file was not written.
     *
     * Pinned because that sentence is the only thing telling someone their stars did
     * not reach the file, and a line of explanatory text is easy to lose in an edit.
     */
    @Test
    fun `rating row when the file could not be written`() {
        capture("rating-write-refused") {
            StarRatingRow(rating = 3, enabled = true, accent = accent, writeRefused = true, onRate = {})
        }
    }

    @Test
    fun `mini player with a track playing`() {
        capture("mini-player-playing") {
            MiniPlayerBar(
                session = playingSession,
                onExpand = {},
                onOpenQueue = {},
                onTogglePlayPause = {},
                onSkipNext = {},
            )
        }
    }

    /**
     * Nothing loaded — what the app shows on a cold start, and the state easiest to
     * break while working only on the one where music is playing.
     */
    @Test
    fun `mini player with nothing playing`() {
        capture("mini-player-empty") {
            MiniPlayerBar(
                session = null,
                onExpand = {},
                onOpenQueue = {},
                onTogglePlayPause = {},
                onSkipNext = {},
            )
        }
    }
}
