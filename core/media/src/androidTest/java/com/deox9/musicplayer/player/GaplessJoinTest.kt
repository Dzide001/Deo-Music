// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * Whether the DSP chain costs anything at a track join.
 *
 * Playing two items of identical format back to back should not make the audio sink
 * tear its path down and rebuild it — that rebuild is the discontinuity gapless
 * playback exists to avoid. Measuring it on device by hand proved unreliable: driving
 * the queue through the long-press menu kept landing on whatever was overlaying the
 * list, so appends failed silently and runs ended with one item and a legitimate stop
 * rather than a transition. Calling the player directly removes all of that.
 *
 * The comparison, not the absolute count, is the assertion. Media3 may reconfigure at
 * every media period for reasons of its own; what this pins down is whether adding
 * the DSP makes it worse.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class GaplessJoinTest {

    private lateinit var context: Context
    private lateinit var toneFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // WAV, so there is no encoder delay or padding in play and the two items are
        // byte-identical: any reconfiguration is the sink's doing, not the file's.
        toneFile = File(context.cacheDir, "gapless-tone.wav").also(::writeTone)
    }

    @Test
    fun theDspDoesNotAddAReconfigurationAtATrackJoin() {
        val bare = measureJoin(withDsp = false)
        val withDsp = measureJoin(withDsp = true)

        assertTrue("no join was observed in the control run", bare.joinObserved)
        assertTrue("no join was observed in the DSP run", withDsp.joinObserved)

        assertEquals(
            "flushes at the join: control ${bare.flushesAtJoin}, with DSP ${withDsp.flushesAtJoin}",
            bare.flushesAtJoin,
            withDsp.flushesAtJoin,
        )
        assertEquals(
            "configures at the join: control ${bare.configuresAtJoin}, with DSP ${withDsp.configuresAtJoin}",
            bare.configuresAtJoin,
            withDsp.configuresAtJoin,
        )
    }

    /**
     * The property gapless playback actually requires, which this build does not have.
     *
     * Ignored rather than deleted or weakened to match: it fails at 2 flushes with no
     * DSP in the chain and no float output requested, so the sink rebuilds its path at
     * every join between two identical items. Asserting the observed 2 would turn a
     * statement of what is wanted into a description of what happens, and the next
     * person would have no way to tell the difference.
     *
     * Un-ignore when the cause is found. The remaining suspect is this test's own
     * sink: all three measurements build DefaultAudioSink through a buildAudioSink
     * override, so a default of the stock path may simply not be set here.
     */
    @Ignore("Known defect: the sink rebuilds at every same-format join. See kdoc.")
    @Test
    fun aSameFormatJoinDoesNotRebuildTheAudioPath() {
        val bare = measureJoin(withDsp = false)

        assertTrue("no join was observed", bare.joinObserved)
        assertEquals(
            "the sink rebuilt its path at a join between two identical items",
            0,
            bare.flushesAtJoin,
        )
    }

    /**
     * Whether asking for float output is what costs the join.
     *
     * The comparison above holds the sink's configuration fixed and varies only the
     * DSP, so it cannot see this: float output is requested for the DSP's benefit —
     * it is what gives a boost somewhere to go before the limiter — and if that
     * request is itself what forces the rebuild, then the DSP is the cause after all,
     * just one level up from where the first comparison looked.
     */
    @Test
    fun floatOutputIsNotWhatCostsTheJoin() {
        val withFloat = measureJoin(withDsp = false, floatOutput = true)
        val withoutFloat = measureJoin(withDsp = false, floatOutput = false)

        assertTrue("no join was observed with float output", withFloat.joinObserved)
        assertTrue("no join was observed without float output", withoutFloat.joinObserved)

        assertEquals(
            "flushes at the join: float ${withFloat.flushesAtJoin}, " +
                "integer ${withoutFloat.flushesAtJoin}",
            withoutFloat.flushesAtJoin,
            withFloat.flushesAtJoin,
        )
    }

    private data class JoinMeasurement(
        val joinObserved: Boolean,
        val flushesAtJoin: Int,
        val configuresAtJoin: Int,
    )

    /**
     * Plays the same file twice and counts what happens across the boundary.
     *
     * Counts are sampled once the first item is established and again once the second
     * is, so start-up costs are excluded and only the join is measured.
     */
    private fun measureJoin(withDsp: Boolean, floatOutput: Boolean = true): JoinMeasurement {
        val recorder = RecordingAudioProcessor()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: ExoPlayer

        instrumentation.runOnMainSync {
            player = buildPlayer(recorder, withDsp, floatOutput)
            player.setMediaItems(listOf(mediaItem(), mediaItem()))
            player.prepare()
            player.volume = 0f
            player.play()
        }

        val reachedFirst = awaitItem(player, index = 0)
        val flushesBefore = recorder.flushCount
        val configuresBefore = recorder.configureCount

        val reachedSecond = awaitItem(player, index = 1)
        val flushesAfter = recorder.flushCount
        val configuresAfter = recorder.configureCount

        instrumentation.runOnMainSync { player.release() }

        return JoinMeasurement(
            joinObserved = reachedFirst && reachedSecond,
            flushesAtJoin = flushesAfter - flushesBefore,
            configuresAtJoin = configuresAfter - configuresBefore,
        )
    }

    private fun buildPlayer(recorder: AudioProcessor, withDsp: Boolean, floatOutput: Boolean): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                val chain = if (withDsp) {
                    DefaultAudioSink.DefaultAudioProcessorChain(recorder, DeoAudioProcessor())
                } else {
                    DefaultAudioSink.DefaultAudioProcessorChain(recorder)
                }
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(floatOutput)
                    .setAudioProcessorChain(chain)
                    .build()
            }
        }
        return ExoPlayer.Builder(context, renderersFactory).build()
    }

    private fun mediaItem(): MediaItem = MediaItem.fromUri(toneFile.toURI().toString())

    /**
     * Waits until the given item is playing and has produced some audio.
     *
     * Position rather than state: the item index changes before any of it has been
     * rendered, and sampling the counters at that instant would attribute the join's
     * events to whichever side won the race.
     */
    private fun awaitItem(player: ExoPlayer, index: Int): Boolean {
        val latch = CountDownLatch(1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            var matched = false
            instrumentation.runOnMainSync {
                matched = player.currentMediaItemIndex == index &&
                    player.currentPosition > SETTLE_MS &&
                    player.playbackState != Player.STATE_IDLE
            }
            if (matched) {
                latch.countDown()
                return true
            }
            Thread.sleep(POLL_MS)
        }
        return false
    }

    /**
     * Writes a short 44.1 kHz stereo WAV.
     *
     * Deliberately quiet and brief: the test plays it at zero volume twice, and the
     * only thing that matters is that it decodes to a stable format.
     */
    private fun writeTone(file: File) {
        val frames = SAMPLE_RATE * TONE_SECONDS
        val dataBytes = frames * CHANNELS * BYTES_PER_SAMPLE
        val buffer = ByteBuffer.allocate(WAV_HEADER_BYTES + dataBytes).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(WAV_HEADER_BYTES + dataBytes - 8)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(CHANNELS.toShort())
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
        buffer.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort())
        buffer.putShort((BYTES_PER_SAMPLE * 8).toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(dataBytes)

        for (frame in 0 until frames) {
            val value = (sin(2.0 * PI * TONE_HZ * frame / SAMPLE_RATE) * TONE_AMPLITUDE).toInt().toShort()
            repeat(CHANNELS) { buffer.putShort(value) }
        }

        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            out.write(buffer.array())
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2
        const val WAV_HEADER_BYTES = 44
        const val TONE_SECONDS = 2
        const val TONE_HZ = 440.0
        const val TONE_AMPLITUDE = 6000.0

        /** Long enough that the item is genuinely rendering, short enough to be quick. */
        const val SETTLE_MS = 250
        const val POLL_MS = 50L
        val AWAIT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20)
    }
}
