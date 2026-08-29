// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.deox9.musicplayer.audio.LoudnessMeter
import com.deox9.musicplayer.audio.LoudnessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Measures a file's loudness by decoding it.
 *
 * This is what makes ReplayGain work on a library that has never been tagged — which
 * is most libraries assembled from downloads and messaging apps. Reading a tag is
 * free but only a minority of files carry one; measuring costs a full decode, so it
 * belongs in a background pass, never on the path to starting playback.
 *
 * Decoding is done with MediaCodec directly rather than through the player: the
 * player renders in real time, and measuring a four-minute track has no reason to
 * take four minutes.
 */
class LoudnessAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Decodes and measures, or returns null if the file cannot be read.
     *
     * Null covers a genuinely broken file, an unsupported codec, and a URI that no
     * longer resolves. None of those is worth distinguishing here — the caller's only
     * options are to record a measurement or not.
     */
    @Suppress("ReturnCount")
    fun measure(mediaUri: String, cancelled: () -> Boolean = { false }): LoudnessResult? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        return try {
            extractor.setDataSource(context, Uri.parse(mediaUri), null)
            val trackIndex = firstAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            decodeAndMeasure(extractor, codec, cancelled)
        } catch (error: Exception) {
            // Deliberately broad. Every layer here throws something different —
            // IllegalArgument for a bad codec, IllegalState for a codec in the wrong
            // state, IOException for the file, and MediaCodec.CodecException on top.
            // A file that cannot be measured is not an error worth propagating: the
            // pass moves on to the next one.
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }

    @Suppress("ReturnCount")
    private fun decodeAndMeasure(
        extractor: MediaExtractor,
        codec: MediaCodec,
        cancelled: () -> Boolean,
    ): LoudnessResult? {
        val bufferInfo = MediaCodec.BufferInfo()
        var meter: LoudnessMeter? = null
        var sawInputEnd = false
        var sawOutputEnd = false

        while (!sawOutputEnd) {
            if (cancelled()) return null

            if (!sawInputEnd) sawInputEnd = feedInput(extractor, codec)

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    meter = consumeOutput(codec, outputIndex, bufferInfo, meter)
                    codec.releaseOutputBuffer(outputIndex, false)
                    sawOutputEnd = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // A decoder that produces nothing at all would otherwise spin
                    // here forever on a file it cannot handle.
                    if (sawInputEnd) return meter?.result()
                }
                else -> Unit
            }
        }

        return meter?.result()
    }

    /**
     * Feeds one decoded buffer to the meter, creating it on the first one.
     *
     * The meter cannot be built before decoding starts: the output format — and with
     * it the sample rate the K-weighting filters have to be designed for — is only
     * known once the decoder has produced something.
     */
    private fun consumeOutput(
        codec: MediaCodec,
        outputIndex: Int,
        info: MediaCodec.BufferInfo,
        existing: LoudnessMeter?,
    ): LoudnessMeter? {
        val output = codec.getOutputBuffer(outputIndex)
        if (output == null || info.size <= 0) return existing

        val format = codec.outputFormat
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val meter = existing ?: LoudnessMeter(
            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channelCount = channelCount,
        )

        val sampleCount = readSamples(output, info, format) { needed ->
            if (scratch.size < needed) scratch = FloatArray(needed)
            scratch
        }
        meter.add(scratch, sampleCount / channelCount)
        return meter
    }

    /** Reused across buffers; the decoder's buffer size does not change mid-stream. */
    private var scratch = FloatArray(0)

    /**
     * Hands the decoder its next chunk. Returns true once the stream is exhausted.
     */
    private fun feedInput(extractor: MediaExtractor, codec: MediaCodec): Boolean {
        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex < 0) return false

        val buffer = codec.getInputBuffer(inputIndex)
        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }

        codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    /**
     * Reads one decoded buffer into floats.
     *
     * Decoders emit 16-bit PCM unless asked otherwise, and some emit float; both are
     * handled because assuming one of them is how a measurement silently comes out
     * scaled by 32768.
     */
    private fun readSamples(
        output: ByteBuffer,
        info: MediaCodec.BufferInfo,
        format: MediaFormat,
        scratchFor: (Int) -> FloatArray,
    ): Int {
        output.order(ByteOrder.nativeOrder())
        output.position(info.offset)
        output.limit(info.offset + info.size)

        val isFloat = format.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING) == ENCODING_PCM_FLOAT
        return if (isFloat) {
            val floats = output.asFloatBuffer()
            val count = floats.remaining()
            val scratch = scratchFor(count)
            floats.get(scratch, 0, count)
            count
        } else {
            val shorts = output.asShortBuffer()
            val count = shorts.remaining()
            val scratch = scratchFor(count)
            for (index in 0 until count) scratch[index] = shorts.get(index) / SHORT_SCALE
            count
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private companion object {
        const val TIMEOUT_US = 10_000L
        const val SHORT_SCALE = 32768f

        /** AudioFormat.ENCODING_PCM_FLOAT, without pulling in the media package. */
        const val ENCODING_PCM_FLOAT = 4
    }
}
