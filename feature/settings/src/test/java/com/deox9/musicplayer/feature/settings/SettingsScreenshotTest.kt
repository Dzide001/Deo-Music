// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deox9.musicplayer.designsystem.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images for the settings sheet's building blocks.
 *
 * The sheet itself is still driven by a view model and is not covered; these are the
 * two controls it is mostly made of. SettingToggleRow needed nothing — it was
 * already written to take a value and a callback — and ThemeModeRow had to be handed
 * its choice instead of reaching for one.
 *
 * The toggle row is worth pinning for a reason that is not visual: the switch lives
 * on the row rather than beside it, so the label and control are a single node with
 * one name. That is easy to undo by accident while rearranging a layout, and the
 * only sign is that a screen reader starts announcing an anonymous switch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsScreenshotTest {

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }

    @Test
    fun `toggle row on`() {
        capture("setting-toggle-on") {
            SettingToggleRow(title = "Skip silence", checked = true, onCheckedChange = {})
        }
    }

    @Test
    fun `toggle row off`() {
        capture("setting-toggle-off") {
            SettingToggleRow(title = "Skip silence", checked = false, onCheckedChange = {})
        }
    }

    @Test
    fun `theme choice following the system`() {
        capture("theme-mode-system") {
            ThemeModeRow(selected = ThemeMode.System, onSelect = {})
        }
    }

    /** A choice that overrides the system, which is the visually distinct case. */
    @Test
    fun `theme choice forced dark`() {
        capture("theme-mode-dark") {
            ThemeModeRow(selected = ThemeMode.Dark, onSelect = {})
        }
    }
}
