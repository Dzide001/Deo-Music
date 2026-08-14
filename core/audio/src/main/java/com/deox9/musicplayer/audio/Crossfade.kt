// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The shape a fade follows from one level to another.
 *
 * The choice is not cosmetic, and the three here are not variations on a theme —
 * each is correct for a different situation, and using the wrong one is audible.
 */
enum class CrossfadeCurve {

    /**
     * Straight line in amplitude.
     *
     * The obvious choice and the wrong one for two overlapping tracks: at the
     * halfway point both sit at 0.5, and two uncorrelated signals at 0.5 sum to
     * 0.707 of full power, not 1.0. That is a 3 dB dip in the middle of every
     * transition — the sag people hear and describe as the music "ducking".
     *
     * Kept because it is right for a *correlated* pair, where the two signals are
     * near-identical and add as amplitude rather than power.
     */
    Linear,

    /**
     * Quarter-cycle sine and cosine, so the two sides sum to constant power.
     *
     * sin²+cos² = 1 for every value of t, which is exactly the property linear
     * lacks. This is the right curve when two different tracks overlap, because
     * uncorrelated signals add as power.
     */
    EqualPower,

    /**
     * A straight line in decibels rather than in amplitude.
     *
     * Right for a fade to or from silence with nothing on the other side, because
     * loudness is perceived roughly logarithmically: a linear amplitude fade drops
     * most of its perceived level in the first fraction of the time and then seems
     * to hang. This one falls at a steady rate to the ear.
     *
     * A true dB-linear fade never reaches zero, so it is run down to [FLOOR_DB] and
     * the endpoints are pinned — an endpoint that is nearly-but-not-quite silent is
     * a click waiting to happen.
     */
    Logarithmic,
    ;

    /** Gain for the incoming side, from silence at 0 to full at 1. */
    fun gainIn(progress: Double): Double {
        val t = progress.coerceIn(0.0, 1.0)
        return when (this) {
            Linear -> t
            EqualPower -> sin(t * PI / 2)
            Logarithmic -> if (t <= 0.0) 0.0 else decibelRamp(t)
        }
    }

    /** Gain for the outgoing side, from full at 0 to silence at 1. */
    fun gainOut(progress: Double): Double {
        val t = progress.coerceIn(0.0, 1.0)
        return when (this) {
            Linear -> 1.0 - t
            EqualPower -> cos(t * PI / 2)
            Logarithmic -> if (t >= 1.0) 0.0 else decibelRamp(1.0 - t)
        }
    }

    /** [FLOOR_DB] at t=0 rising to 0 dB at t=1, as a linear gain. */
    private fun decibelRamp(t: Double): Double = TEN.pow(FLOOR_DB * (1.0 - t) / DB_PER_DECADE)

    companion object {
        /**
         * How far down a logarithmic fade travels before it is called silence.
         *
         * 60 dB is the usual convention for a fade-out and is below the noise floor
         * of anything this will be played through.
         */
        const val FLOOR_DB = -60.0

        private const val TEN = 10.0
        private const val DB_PER_DECADE = 20.0

        /** Parses a stored name, falling back rather than throwing on an old value. */
        fun fromName(name: String?): CrossfadeCurve =
            entries.firstOrNull { it.name == name } ?: EqualPower
    }
}

/**
 * When to fade, for how long, and along which curve.
 *
 * Skipping and reaching the end of a track are deliberately separate settings, and
 * the defaults differ, because they are not the same situation:
 *
 * A manual skip is an interruption the listener asked for. Cutting a track dead
 * mid-phrase is jarring, and a fade costs nothing — the rest of that track was
 * being abandoned anyway.
 *
 * An automatic advance is the opposite. Fading there means fading out audio the
 * listener wanted to hear, and it destroys gapless playback: a continuous mix or a
 * live album gets a hole dug in it at every join. So it is off by default, and the
 * listener has to ask for it.
 */
data class CrossfadeSettings(
    val onSkip: Boolean = false,
    val onAutoAdvance: Boolean = false,
    val durationMs: Int = DEFAULT_DURATION_MS,
    val curve: CrossfadeCurve = CrossfadeCurve.EqualPower,
) {
    val effectiveDurationMs: Int get() = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)

    companion object {
        const val DEFAULT_DURATION_MS = 400

        /**
         * Below about 50 ms a fade stops being a fade and becomes a click with extra
         * steps; above a few seconds a skip stops feeling like a response to the
         * button that was pressed.
         */
        const val MIN_DURATION_MS = 50
        const val MAX_DURATION_MS = 5_000
    }
}

/**
 * A fade in progress, as a function of how many frames have elapsed.
 *
 * Frames rather than milliseconds because this is read on the audio thread, one
 * buffer at a time, and converting from wall-clock time there would drift against
 * the samples actually being produced.
 */
class FadeRamp(
    private val curve: CrossfadeCurve,
    val direction: FadeDirection,
    val durationFrames: Int,
) {
    init {
        require(durationFrames > 0) { "a fade needs a positive length, was $durationFrames" }
    }

    /**
     * Gain at [frame], saturating past the end.
     *
     * Past the end it holds the final value rather than wrapping or going silent, so
     * a buffer that overruns the fade keeps playing at the level the fade left it.
     */
    fun gainAt(frame: Int): Float {
        val progress = (frame.toDouble() / durationFrames).coerceIn(0.0, 1.0)
        return when (direction) {
            FadeDirection.In -> curve.gainIn(progress)
            FadeDirection.Out -> curve.gainOut(progress)
        }.toFloat()
    }

    fun isComplete(frame: Int): Boolean = frame >= durationFrames

    companion object {
        /** Builds a ramp of [durationMs] at [sampleRate], never shorter than one frame. */
        fun of(
            curve: CrossfadeCurve,
            direction: FadeDirection,
            durationMs: Int,
            sampleRate: Int,
        ): FadeRamp = FadeRamp(
            curve = curve,
            direction = direction,
            durationFrames = (durationMs.toLong() * sampleRate / MS_PER_SECOND)
                .toInt()
                .coerceAtLeast(1),
        )

        private const val MS_PER_SECOND = 1000
    }
}

enum class FadeDirection { In, Out }
