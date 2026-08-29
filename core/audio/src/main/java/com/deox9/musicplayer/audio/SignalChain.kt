// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What is actually happening to the audio, end to end.
 *
 * Every other part of the player asserts things about the signal — that ReplayGain is
 * applied, that the equaliser is active, that the file is lossless — and none of them
 * can be checked. This is the one place those claims are shown rather than made, which
 * is also what makes a wrong one findable.
 *
 * Fields are nullable because the chain is discovered as playback starts: the decoder
 * names itself after it is created, the output format is only known once the sink has
 * one. Absent is displayed as absent rather than guessed at.
 */
data class SignalChain(
    val source: StreamFormat? = null,
    val decoderName: String? = null,
    val stages: List<DspStage> = emptyList(),
    val output: StreamFormat? = null,
    /** Where the audio is going, as a listener would name it. */
    val outputRoute: String? = null,
    /** Whether a saved per-output profile is shaping the sound rather than the global settings. */
    val usingOutputProfile: Boolean = false,
)

/**
 * A format at some point in the chain.
 *
 * [bitDepth] is null for a compressed source, where the number would be meaningless —
 * an MP3 has no bit depth, only the decoder's output does, and reporting one is how a
 * player ends up claiming a lossy file is 16-bit.
 */
data class StreamFormat(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null,
    val bitrateKbps: Int? = null,
) {
    fun describe(): String {
        val parts = buildList {
            codec?.takeIf { it.isNotBlank() }?.let { add(it) }
            sampleRateHz?.let { add(formatSampleRate(it)) }
            bitDepth?.let { add("$it-bit") }
            channelCount?.let { add(describeChannels(it)) }
            bitrateKbps?.takeIf { it > 0 }?.let { add("$it kbps") }
        }
        return if (parts.isEmpty()) "unknown" else parts.joinToString(" · ")
    }
}

/**
 * One processing step, in the order it runs.
 *
 * An inactive stage is still listed. "Limiter — inactive" tells you something; a stage
 * silently missing from the list tells you nothing, and leaves you unable to tell a
 * disabled stage from one that failed to load.
 */
data class DspStage(
    val name: String,
    val detail: String,
    val active: Boolean,
)

/** Builds the stage list from what the chain is configured to do. */
object SignalChainStages {

    fun from(config: AudioChainConfig, limiterReductionDb: Double): List<DspStage> = listOf(
        gainStage(config.gainDb),
        equaliserStage(config.bands),
        limiterStage(config.limiterEnabled, limiterReductionDb),
    )

    private fun gainStage(gainDb: Double): DspStage = DspStage(
        name = "ReplayGain",
        detail = if (gainDb == 0.0) "no adjustment" else "%+.1f dB".format(gainDb),
        active = gainDb != 0.0,
    )

    private fun equaliserStage(bands: List<EqBand>): DspStage {
        val active = bands.filterNot { it.isTransparent }
        return DspStage(
            name = "Equaliser",
            detail = when {
                active.isEmpty() -> "flat"
                active.size == 1 -> describeBand(active.first())
                else -> "${active.size} bands"
            },
            active = active.isNotEmpty(),
        )
    }

    /**
     * The limiter reports the largest reduction it has had to apply.
     *
     * Zero means it is in the path and has not needed to act, which is the normal
     * case and different from it being switched off.
     */
    private fun limiterStage(enabled: Boolean, reductionDb: Double): DspStage = DspStage(
        name = "Limiter",
        detail = when {
            !enabled -> "off"
            abs(reductionDb) < LIMITER_EPSILON_DB -> "no reduction"
            else -> "peak reduction %.1f dB".format(abs(reductionDb))
        },
        active = enabled,
    )

    private fun describeBand(band: EqBand): String {
        val where = formatFrequency(band.frequencyHz)
        return if (band.gainDb == 0.0) where else "%+.1f dB at %s".format(band.gainDb, where)
    }

    private const val LIMITER_EPSILON_DB = 0.05
}

/**
 * A MIME type as the name people know the format by.
 *
 * Taking the part after the slash and upper-casing it gives "MPEG" for an MP3 and
 * "MP4A-LATM" for AAC — technically the subtype, and useless to anyone reading a
 * readout to find out what their file is.
 */
fun codecLabelFor(mimeType: String?): String? {
    val mime = mimeType?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return when (mime) {
        "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2" -> "MP3"
        "audio/mp4a-latm", "audio/aac" -> "AAC"
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/opus" -> "Opus"
        "audio/vorbis", "audio/ogg" -> "Vorbis"
        "audio/alac" -> "ALAC"
        "audio/raw" -> "PCM"
        "audio/wav", "audio/x-wav" -> "WAV"
        "audio/amr", "audio/amr-wb" -> "AMR"
        "audio/ac3", "audio/eac3" -> "Dolby Digital"
        else -> mime.substringAfterLast('/').uppercase()
    }
}

internal fun formatSampleRate(hz: Int): String {
    val khz = hz / 1000.0
    return if (khz == khz.roundToInt().toDouble()) "${khz.roundToInt()} kHz" else "%.1f kHz".format(khz)
}

internal fun formatFrequency(hz: Double): String =
    if (hz >= 1000.0) "%.1f kHz".format(hz / 1000.0) else "${hz.roundToInt()} Hz"

/**
 * Channel counts as names.
 *
 * "2 channels" is technically right and tells a listener nothing; "stereo" is what
 * they are looking for when they open this.
 */
internal fun describeChannels(count: Int): String = when (count) {
    1 -> "mono"
    2 -> "stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count channels"
}
