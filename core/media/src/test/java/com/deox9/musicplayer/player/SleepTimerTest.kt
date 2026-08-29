// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    private val now = 1_000_000L

    @Test
    fun `no timer is set by default`() {
        val timer = SleepTimer()

        assertFalse(timer.state.value.isActive)
        assertNull(timer.state.value.remainingMs(now))
    }

    @Test
    fun `starting a timer sets a deadline that counts down`() {
        val timer = SleepTimer()

        timer.start(durationMs = 30 * 60_000L, finishTrack = false, nowElapsedMs = now)

        assertTrue(timer.state.value.isActive)
        assertEquals(30 * 60_000L, timer.state.value.remainingMs(now))
        assertEquals(29 * 60_000L, timer.state.value.remainingMs(now + 60_000L))
    }

    /** A countdown that goes negative would render as "-3:12 remaining". */
    @Test
    fun `remaining time stops at zero rather than going negative`() {
        val timer = SleepTimer()
        timer.start(durationMs = 60_000L, finishTrack = false, nowElapsedMs = now)

        assertEquals(0L, timer.state.value.remainingMs(now + 120_000L))
    }

    @Test
    fun `a timer expires once its deadline passes`() {
        val timer = SleepTimer()
        timer.start(durationMs = 60_000L, finishTrack = false, nowElapsedMs = now)
        val state = timer.state.value

        assertFalse(state.hasExpired(now + 59_999L))
        assertTrue(state.hasExpired(now + 60_000L))
        assertTrue(state.hasExpired(now + 60_001L))
    }

    @Test
    fun `an unset timer never counts as expired`() {
        assertFalse(SleepTimerState().hasExpired(Long.MAX_VALUE))
    }

    @Test
    fun `cancelling clears the deadline`() {
        val timer = SleepTimer()
        timer.start(durationMs = 60_000L, finishTrack = false, nowElapsedMs = now)

        timer.cancel()

        assertFalse(timer.state.value.isActive)
    }

    /** Setting a new length replaces the old one rather than stacking. */
    @Test
    fun `starting again replaces the running timer`() {
        val timer = SleepTimer()
        timer.start(durationMs = 60 * 60_000L, finishTrack = false, nowElapsedMs = now)

        timer.start(durationMs = 5 * 60_000L, finishTrack = true, nowElapsedMs = now)

        assertEquals(5 * 60_000L, timer.state.value.remainingMs(now))
        assertTrue(timer.state.value.finishTrack)
    }

    @Test
    fun `the finish-track choice is remembered`() {
        val timer = SleepTimer()

        timer.start(durationMs = 60_000L, finishTrack = true, nowElapsedMs = now)

        assertTrue(timer.state.value.finishTrack)
    }

    /** A negative duration is a caller's mistake and must not set a deadline in the past. */
    @Test
    fun `a negative duration expires immediately rather than in the past`() {
        val timer = SleepTimer()

        timer.start(durationMs = -5_000L, finishTrack = false, nowElapsedMs = now)

        assertEquals(0L, timer.state.value.remainingMs(now))
        assertTrue(timer.state.value.hasExpired(now))
    }
}
