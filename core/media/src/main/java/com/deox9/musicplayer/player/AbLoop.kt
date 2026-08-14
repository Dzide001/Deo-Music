// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A section of one track, repeated.
 *
 * For learning a solo, drilling a language phrase, or sitting inside eight bars of a
 * mix. The two points are set while listening rather than typed, so the interesting
 * question is not how to loop but what to do with the half-finished states someone
 * inevitably creates: an A with no B, a B before its A, points left over from a
 * different track.
 */
data class AbLoopState(
    val startMs: Long? = null,
    val endMs: Long? = null,
    /** Which track the points belong to; they mean nothing against any other. */
    val trackUri: String = "",
) {
    /** Both points set, in the right order, and far enough apart to be audible. */
    val isActive: Boolean
        get() {
            val start = startMs
            val end = endMs
            return start != null && end != null && end - start >= MINIMUM_LENGTH_MS
        }

    val isWaitingForEnd: Boolean
        get() = startMs != null && endMs == null

    /**
     * Whether playback has run past the end of the loop.
     *
     * Only when the loop is complete: an A with no B is someone mid-gesture, and
     * jumping them back to A while they are still choosing B would make the second
     * point impossible to set.
     */
    fun hasPassedEnd(positionMs: Long): Boolean =
        isActive && endMs != null && positionMs >= endMs

    companion object {
        /**
         * Shorter than this is a mis-tap rather than a loop.
         *
         * Half a second is already very short for something meant to be listened to,
         * and looping a 20 ms window produces a click at a rate that sounds like a
         * fault in the app rather than a feature.
         */
        const val MINIMUM_LENGTH_MS = 500L
    }
}

/**
 * Holds the loop points.
 *
 * Same shape as the sleep timer and for the same reason: the service acts on it and
 * the UI displays it, and they share a process.
 */
@Singleton
class AbLoop @Inject constructor() {

    private val _state = MutableStateFlow(AbLoopState())
    val state: StateFlow<AbLoopState> = _state.asStateFlow()

    /**
     * Sets the next point from the current position.
     *
     * One control rather than two, because that is how it is used: listen, tap at
     * the start, listen, tap at the end. A third tap clears, so the same button
     * cycles rather than needing a separate reset nobody can find.
     */
    fun mark(positionMs: Long, trackUri: String) {
        val current = _state.value.takeIf { it.trackUri == trackUri } ?: AbLoopState(trackUri = trackUri)
        _state.value = when {
            current.startMs == null -> current.copy(startMs = positionMs, trackUri = trackUri)

            current.endMs == null -> {
                // Marked before A, which happens when someone scrubs back to catch a
                // phrase they have already passed. Taking it as the new start is
                // friendlier than refusing it, and refusing it silently is worse
                // still.
                if (positionMs <= current.startMs + AbLoopState.MINIMUM_LENGTH_MS) {
                    current.copy(startMs = positionMs, endMs = null)
                } else {
                    current.copy(endMs = positionMs)
                }
            }

            else -> AbLoopState(trackUri = trackUri)
        }
    }

    fun clear() {
        _state.value = AbLoopState()
    }

    /** Points belong to one track, so a track change ends the loop. */
    fun clearIfTrackChanged(trackUri: String) {
        if (_state.value.trackUri != trackUri) clear()
    }
}
