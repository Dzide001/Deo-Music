// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

/**
 * Decides whether a queue change should open Now Playing.
 *
 * Replacing the queue means the listener started something new and wants to see it.
 * Appending to it — play-next, add-to-queue — means they are still browsing, and
 * throwing the player over the list they are reading is the rudest thing a music app
 * can do. Telling those apart is the whole job.
 *
 * A class rather than four remembered booleans in the root composable, because it is
 * a small state machine with real branching: it was the largest single contributor
 * to that composable's complexity, and as inline state it could not be tested at all.
 */
class QueueExpansion {

    private var previousSize: Int = 0
    private var previousSignature: String = ""
    private var initialised: Boolean = false
    private var suppressed: Boolean = false

    /**
     * Whether the player should open, given the queue as it now stands.
     *
     * Returns false the first time it sees a queue: a queue restored at launch is
     * not something the listener just chose, and opening the player over their
     * library on every cold start would be maddening.
     */
    fun shouldExpand(queueUris: List<String>): Boolean {
        if (queueUris.isEmpty()) {
            reset()
            return false
        }

        val signature = queueUris.joinToString("|")

        if (!initialised) {
            remember(queueUris.size, signature)
            initialised = true
            return false
        }

        if (suppressed) {
            remember(queueUris.size, signature)
            return false
        }

        // An append keeps every earlier entry in place, so the old signature is a
        // prefix of the new one. Anything else replaced the queue.
        val isAppend = previousSignature.isNotBlank() &&
            queueUris.size >= previousSize &&
            queueUris.take(previousSize).joinToString("|") == previousSignature
        val replaced = previousSize > 0 && signature != previousSignature && !isAppend

        remember(queueUris.size, signature)
        return replaced
    }

    /**
     * Stops the next change opening the player.
     *
     * For the cases where the app itself is rearranging the queue — restoring a
     * saved one, say — and the listener did not ask to be taken anywhere.
     */
    fun suppress(suppress: Boolean) {
        suppressed = suppress
    }

    private fun reset() {
        previousSize = 0
        previousSignature = ""
        initialised = false
        suppressed = false
    }

    private fun remember(size: Int, signature: String) {
        previousSize = size
        previousSignature = signature
    }
}
