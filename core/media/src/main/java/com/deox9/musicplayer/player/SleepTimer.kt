// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A pending stop.
 *
 * The deadline is an instant rather than a countdown, so nothing has to tick it down
 * and it cannot drift. It is [SystemClock.elapsedRealtime], not wall-clock time,
 * because wall-clock jumps when the network corrects the phone's clock or the user
 * crosses a timezone — either would cut the music off early or leave it playing all
 * night.
 */
data class SleepTimerState(
    val endsAtElapsedMs: Long? = null,
    /**
     * Whether to let the current track finish rather than stopping mid-song.
     *
     * The reason someone sets a sleep timer for 30 minutes is rarely that they mean
     * exactly 30 minutes; it is that they want it to stop soon and not be jarred.
     */
    val finishTrack: Boolean = false,
) {
    val isActive: Boolean get() = endsAtElapsedMs != null

    /** Milliseconds left, never negative, or null when no timer is set. */
    fun remainingMs(nowElapsedMs: Long): Long? =
        endsAtElapsedMs?.let { (it - nowElapsedMs).coerceAtLeast(0L) }

    /** True once the deadline has passed. */
    fun hasExpired(nowElapsedMs: Long): Boolean =
        endsAtElapsedMs != null && nowElapsedMs >= endsAtElapsedMs
}

/**
 * Holds the sleep timer, so the service can act on it and the UI can show it.
 *
 * A singleton for the same reason the signal chain readout is one: the service and
 * the UI are the same process, and routing this through session extras would buy
 * nothing but indirection.
 */
@Singleton
class SleepTimer @Inject constructor() {

    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    /** Starts a timer [durationMs] from now, replacing any already running. */
    fun start(
        durationMs: Long,
        finishTrack: Boolean,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ) {
        _state.value = SleepTimerState(
            endsAtElapsedMs = nowElapsedMs + durationMs.coerceAtLeast(0L),
            finishTrack = finishTrack,
        )
    }

    fun cancel() {
        _state.value = SleepTimerState()
    }

    companion object {
        /** The lengths offered, in minutes. */
        val PRESET_MINUTES = listOf(5, 15, 30, 45, 60, 90)

        /**
         * How long the fade before the stop takes.
         *
         * Long enough to be a fade rather than a cut, and long by comparison with a
         * skip fade, because nobody is waiting on it — the point is not to wake
         * anyone up.
         */
        const val FADE_OUT_MS = 5_000
    }
}
