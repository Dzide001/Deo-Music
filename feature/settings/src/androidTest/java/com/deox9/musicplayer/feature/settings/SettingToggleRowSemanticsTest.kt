// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
 * The settings sheet's rows, checked the same way as the player's.
 *
 * Both had the same defect and it is the kind that comes back the moment someone
 * writes a new row by copying an old one, so both are pinned.
 */
@RunWith(AndroidJUnit4::class)
class SettingToggleRowSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setRow(checked: Boolean = false, onChange: (Boolean) -> Unit = {}) {
        composeRule.setContent {
            SettingToggleRow(title = "Fade between tracks", checked = checked, onCheckedChange = onChange)
        }
    }

    @Test
    fun theLabelAndTheToggleAreOneNode() {
        setRow(checked = false)

        composeRule.onNodeWithText("Fade between tracks").assertIsOff()
    }

    @Test
    fun theNodeReportsWhichWayItIsSet() {
        setRow(checked = true)

        composeRule.onNodeWithText("Fade between tracks").assertIsOn()
    }

    @Test
    fun theNodeIsAnnouncedAsASwitch() {
        setRow()

        val node = composeRule.onNodeWithText("Fade between tracks").fetchSemanticsNode()

        assertEquals(Role.Switch, node.config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun theWholeRowTogglesRatherThanJustTheSwitch() {
        var state = false
        setRow(checked = false) { state = it }

        composeRule.onNodeWithText("Fade between tracks").performClick()

        assertTrue(state)
    }
}
