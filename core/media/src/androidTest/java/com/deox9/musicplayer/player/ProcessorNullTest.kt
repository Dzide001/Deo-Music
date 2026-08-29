// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deox9.musicplayer.audio.AudioChainConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * The null test through the processor the player actually runs.
 *
 * The arithmetic is covered by NullTest in :core:audio. What that cannot reach is
 * this: the conversion in and out of NIO buffers, where the sample values are correct
 * but the bytes need not be. An endianness mistake or a mispositioned buffer here
 * would corrupt every track while every unit test stayed green, so the bytes are
 * compared rather than the numbers.
 */
@RunWith(AndroidJUnit4::class)
class ProcessorNullTest {

    private val sampleRate = 44_100
    private val channels = 2

    /** A tone plus a full-scale sample, as little-endian 16-bit PCM. */
    private fun pcm16(frames: Int): ByteArray {
        val buffer = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder())
        for (frame in 0 until frames) {
            val value = (sin(2.0 * PI * 997.0 * frame / sampleRate) * 8_000).toInt().toShort()
            buffer.putShort(value)
            buffer.putShort((value / 2).toShort())
        }
        return buffer.array()
    }

    /**
     * Runs bytes through a processor and returns what came out.
     *
     * Fed in several pieces, because a single call would not exercise the buffer
     * handling between them, which is where the risk is.
     */
    private fun runThrough(input: ByteArray, config: AudioChainConfig): ByteArray {
        val processor = DeoAudioProcessor()
        processor.setConfig(config)
        processor.configure(
            AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT),
        )
        processor.flush()

        val output = java.io.ByteArrayOutputStream()
        val chunk = 4_096
        var offset = 0
        while (offset < input.size) {
            val length = minOf(chunk, input.size - offset)
            val slice = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder())
            slice.put(input, offset, length)
            slice.flip()
            processor.queueInput(slice)

            val produced = processor.output
            while (produced.hasRemaining()) {
                output.write(produced.get().toInt())
            }
            offset += length
        }
        return output.toByteArray()
    }

    /**
     * The claim the whole DSP rests on: with nothing switched on, the bytes that
     * reach the sink are the bytes the decoder produced.
     */
    @Test
    fun aNeutralProcessorReturnsTheBytesItWasGiven() {
        val input = pcm16(frames = 20_000)

        val output = runThrough(input, AudioChainConfig(limiterEnabled = false))

        assertEquals("length changed", input.size, output.size)
        val firstDifference = input.indices.firstOrNull { input[it] != output[it] }
        assertEquals(
            "first differing byte at $firstDifference",
            null,
            firstDifference,
        )
    }

    /**
     * Deliberately not aligned to the frame size, so a buffer left mispositioned
     * between calls shows up as a shift rather than passing unnoticed.
     */
    @Test
    fun theBytesSurviveBeingFedInAwkwardlySizedPieces() {
        val input = pcm16(frames = 9_999)

        val output = runThrough(input, AudioChainConfig(limiterEnabled = false))

        assertEquals(input.size, output.size)
        assertTrue(
            "bytes differ",
            input.indices.none { input[it] != output[it] },
        )
    }

    /** And the processor is genuinely in the path, or the test above proves nothing. */
    @Test
    fun aProcessorWithGainDoesChangeTheBytes() {
        val input = pcm16(frames = 20_000)

        val output = runThrough(input, AudioChainConfig(gainDb = -6.0, limiterEnabled = false))

        assertTrue(
            "a 6 dB cut left the bytes identical",
            input.indices.any { input[it] != output[it] },
        )
    }
}
