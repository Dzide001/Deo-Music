// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deox9.musicplayer.library.LocalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a screen reader actually gets from a library row.
 *
 * This exists because no static check and no `uiautomator` dump can answer it: the
 * dump exposes Compose's virtual nodes whether or not they are merged, so it shows
 * the title, subtitle and duration as separate entries either way. The merged
 * semantics tree — which is what TalkBack walks — is only visible from here.
 */
@RunWith(AndroidJUnit4::class)
class LocalTrackRowSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val track = LocalTrack(
        id = 1L,
        title = "Grade 1",
        artist = "Stonebwoy",
        album = "Grade 1",
        durationMs = 222_000L,
        contentUri = "content://media/external/audio/media/1",
    )

    private fun setRow(isFavourite: Boolean = false) {
        composeRule.setContent {
            LocalTrackRow(
                track = track,
                isFavourite = isFavourite,
                onToggleFavourite = {},
                onClick = {},
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    @Test
    fun theRowIsOneNodeCarryingTitleArtistAndDuration() {
        setRow()

        // Resolving by title on the merged tree lands on the row itself, so the
        // subtitle and the spoken duration have to be part of the same node.
        composeRule.onNodeWithText("Grade 1", substring = true)
            .assertHasClickAction()
            .assert(hasTextContaining("Stonebwoy"))
            .assert(hasDescriptionContaining("3 minutes 42 seconds"))
    }

    /** Long press is the only way in without these, and TalkBack cannot long press. */
    @Test
    fun theRowExposesItsMenuAsNamedActions() {
        setRow()

        val node = composeRule.onNodeWithText("Grade 1", substring = true).fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions].map { it.label }

        assertEquals(
            listOf("Play next", "Add to queue", "Add to favourites", "Track details"),
            actions,
        )
    }

    @Test
    fun theFavouriteActionSaysWhichWayItGoes() {
        setRow(isFavourite = true)

        val node = composeRule.onNodeWithText("Grade 1", substring = true).fetchSemanticsNode()
        val actions = node.config[SemanticsActions.CustomActions].map { it.label }

        assertTrue(actions.toString(), "Remove favourite" in actions)
    }

    /** A single stop, not three: title, subtitle and duration must not be separate. */
    @Test
    fun theRowDoesNotSplitIntoSeparateStops() {
        setRow()

        val tree = composeRule.onRoot().printToString(maxDepth = 100)
        val clickable = Regex("Actions =.*OnClick").findAll(tree).count()

        assertEquals(tree, 1, clickable)
    }

    private fun hasTextContaining(text: String) = SemanticsMatcher("text contains '$text'") { node ->
        node.spokenStrings().any { text in it }
    }

    private fun hasDescriptionContaining(text: String) =
        SemanticsMatcher("description contains '$text'") { node ->
            node.spokenStrings().any { text in it }
        }

    /** Everything a screen reader would read out of this node, merged. */
    private fun androidx.compose.ui.semantics.SemanticsNode.spokenStrings(): List<String> {
        val texts = config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
        val descriptions = config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        return texts + descriptions
    }
}
