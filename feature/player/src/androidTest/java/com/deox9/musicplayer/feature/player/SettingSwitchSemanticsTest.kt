// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.player

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a screen reader gets from a settings switch.
 *
 * Written from the merged semantics tree rather than a `uiautomator` dump, because
 * the dump cannot answer this: it lists Compose's virtual nodes whether or not they
 * are merged, so an unlabelled toggle looks identical either way. That mistake has
 * already been made once in this project.
 *
 * The defect these were written against: the row was a plain Row with the toggle
 * action on the Switch alone, so the label and the control were two separate stops
 * and the control had no name of its own — TalkBack announced "switch, off" without
 * saying what it switched.
 */
@RunWith(AndroidJUnit4::class)
class SettingSwitchSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setSwitch(checked: Boolean = false, onChange: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            SettingSwitch(label = "Fade when skipping", checked = checked, onChange = onChange)
        }
    }

    /**
     * The whole point: resolving by the label has to land on the toggle itself. If
     * the label is its own node, this finds something with no toggle state.
     */
    @Test
    fun theLabelAndTheToggleAreOneNode() {
        setSwitch(checked = false)

        composeRule.onNodeWithText("Fade when skipping").assertIsOff()
    }

    @Test
    fun theNodeReportsWhichWayItIsSet() {
        setSwitch(checked = true)

        composeRule.onNodeWithText("Fade when skipping").assertIsOn()
    }

    /** Announced as a switch, not as a checkbox or a bare button. */
    @Test
    fun theNodeIsAnnouncedAsASwitch() {
        setSwitch()

        val node = composeRule.onNodeWithText("Fade when skipping").fetchSemanticsNode()
        val role = node.config.getOrNull(SemanticsProperties.Role)

        assertEquals(Role.Switch, role)
    }

    /** The whole row is the target, so it is far larger than the switch alone. */
    @Test
    fun theWholeRowTogglesRatherThanJustTheSwitch() {
        var state = false
        setSwitch(checked = false) { state = it }

        composeRule.onNodeWithText("Fade when skipping").performClick()

        assertTrue(state)
    }

    @Test
    fun theToggleStateIsTheOneComposeReports() {
        setSwitch(checked = true)

        val node = composeRule.onNodeWithText("Fade when skipping").fetchSemanticsNode()

        assertEquals(
            ToggleableState.On,
            node.config.getOrNull(SemanticsProperties.ToggleableState),
        )
    }
}
