// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

/** The filter shapes a band can take. */
enum class EqBandType { Peaking, LowShelf, HighShelf, HighPass, LowPass }

/**
 * One band of a parametric equaliser.
 *
 * Frequency, gain and Q are all free, which is what separates this from the ten-band
 * graphic equaliser it replaces: there, the frequencies are fixed and Q is chosen for
 * you, so a narrow resonance at 3.2 kHz can only be addressed by pulling down a whole
 * octave around it.
 */
data class EqBand(
    val type: EqBandType,
    val frequencyHz: Double,
    val gainDb: Double = 0.0,
    val q: Double = DEFAULT_Q,
    val enabled: Boolean = true,
) {
    /** A band that cannot change anything, whatever its other settings say. */
    val isTransparent: Boolean
        get() = !enabled || (gainDb == 0.0 && type in GAIN_ONLY_TYPES)

    fun coefficients(sampleRate: Int): BiquadCoefficients = when (type) {
        EqBandType.Peaking -> BiquadDesign.peaking(frequencyHz, gainDb, q, sampleRate)
        EqBandType.LowShelf -> BiquadDesign.lowShelf(frequencyHz, gainDb, q, sampleRate)
        EqBandType.HighShelf -> BiquadDesign.highShelf(frequencyHz, gainDb, q, sampleRate)
        EqBandType.HighPass -> BiquadDesign.highPass(frequencyHz, q, sampleRate)
        EqBandType.LowPass -> BiquadDesign.lowPass(frequencyHz, q, sampleRate)
    }

    companion object {
        const val DEFAULT_Q = 1.0

        /**
         * Types whose whole effect is their gain, so zero gain means transparent.
         * A high-pass at 0 dB still filters — its gain is not what it does.
         */
        private val GAIN_ONLY_TYPES = setOf(EqBandType.Peaking, EqBandType.LowShelf, EqBandType.HighShelf)

        /**
         * The ten ISO frequencies the old graphic equaliser's sliders stood for.
         *
         * Kept so existing settings migrate to something equivalent rather than being
         * discarded: each slider becomes a peaking band at its frequency.
         */
        val GRAPHIC_FREQUENCIES = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

        /**
         * A Q of about 1.4 makes ten bands an octave apart meet near their -3 dB
         * points, which is what keeps a graphic equaliser's response smooth rather
         * than lumpy between sliders.
         */
        const val GRAPHIC_Q = 1.414

        /** Converts the stored millibel levels of the old graphic equaliser. */
        fun fromGraphicLevels(millibels: List<Int>): List<EqBand> =
            GRAPHIC_FREQUENCIES.mapIndexed { index, frequency ->
                EqBand(
                    type = EqBandType.Peaking,
                    frequencyHz = frequency,
                    gainDb = (millibels.getOrElse(index) { 0 }) / MILLIBELS_PER_DB,
                    q = GRAPHIC_Q,
                )
            }

        private const val MILLIBELS_PER_DB = 100.0
    }
}

/**
 * A chain of bands applied to interleaved audio.
 *
 * Each band gets its own filter state per channel — a stereo five-band equaliser is
 * ten independent biquads. Sharing state between channels is the classic way to get
 * an equaliser that sounds like it is modulating the stereo image.
 *
 * Transparent bands are dropped rather than run at unity: a ten-band equaliser sitting
 * flat is ten biquads per channel per sample doing nothing, on every frame of every
 * track.
 */
class ParametricEq(
    private val sampleRate: Int,
    private val channelCount: Int,
) {
    private var filters: List<List<Biquad>> = emptyList()

    var bands: List<EqBand> = emptyList()
        private set

    val isTransparent: Boolean get() = filters.isEmpty()

    fun setBands(value: List<EqBand>) {
        bands = value
        val active = value.filterNot { it.isTransparent }
        filters = active.map { band ->
            val coefficients = band.coefficients(sampleRate)
            List(channelCount) { Biquad(coefficients) }
        }
    }

    fun reset() {
        filters.forEach { perChannel -> perChannel.forEach(Biquad::reset) }
    }

    /** Filters in place. Returns immediately when no band is doing anything. */
    fun process(interleaved: FloatArray, frameCount: Int) {
        if (filters.isEmpty()) return

        var index = 0
        repeat(frameCount) {
            for (channel in 0 until channelCount) {
                var sample = interleaved[index].toDouble()
                for (band in filters) sample = band[channel].process(sample)
                interleaved[index] = sample.toFloat()
                index++
            }
        }
    }
}
