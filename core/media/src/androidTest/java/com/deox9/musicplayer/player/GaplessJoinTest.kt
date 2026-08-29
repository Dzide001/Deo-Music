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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
     * A join flushes the processors but keeps the AudioTrack, and those are not the
     * same thing.
     *
     * Reading the flush as an output teardown is the mistake this test exists to stop
     * anyone repeating — I made it. A flush resets the processing pipeline; the
     * AudioTrack surviving is what says the output stream was continuous. Gapless is
     * working at the level that matters, and the earlier conclusion that it was not
     * came from watching the wrong signal.
     *
     * What the flush does cost is processor state. Anything holding samples across
     * buffers loses them here — for the limiter that is its look-ahead line, which is
     * zeroed and then output as silence.
     */
    @Test
    fun aJoinFlushesTheProcessorsButKeepsTheAudioTrack() {
        val measured = measureJoin(withDsp = true)

        assertTrue("no join was observed", measured.joinObserved)
        assertTrue(
            "expected the pipeline to be flushed at the join, saw ${measured.flushesAtJoin}",
            measured.flushesAtJoin > 0,
        )
        assertEquals(
            "the AudioTrack was recreated, so the output was not continuous",
            0,
            measured.audioTracksInitialisedAtJoin,
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
        val audioTracksInitialisedAtJoin: Int,
    )

    /**
     * Counts AudioTrack lifecycle events.
     *
     * The processor counters are a proxy: a flush says the pipeline was reset, which
     * usually but not always means the output was torn down. A new AudioTrack is the
     * discontinuity itself. It is also the only signal available for a stock player,
     * where no processor of ours can be injected to report anything.
     */
    private class AudioTrackCounter : AnalyticsListener {
        private val initialised = AtomicInteger()

        val initialisedCount: Int get() = initialised.get()

        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig,
        ) {
            initialised.incrementAndGet()
        }
    }

    /**
     * The same join, on a player built with no audio-sink override at all.
     *
     * This is the case the other measurements cannot reach. All of them construct
     * DefaultAudioSink themselves, and so does PlaybackService, so a default that the
     * stock path sets and the override misses would look identical to a Media3
     * limitation in every one of them.
     */
    @Test
    fun theStockSinkDoesNotOutdoTheOverriddenOneAtAJoin() {
        val stock = measureJoin(sink = SinkUnderTest.Stock)
        val overridden = measureJoin(sink = SinkUnderTest.OverriddenWithDsp)

        assertTrue("no join was observed on the stock sink", stock.joinObserved)
        assertTrue("no join was observed on the overridden sink", overridden.joinObserved)

        assertEquals(
            "AudioTracks created at the join: stock ${stock.audioTracksInitialisedAtJoin}, " +
                "overridden ${overridden.audioTracksInitialisedAtJoin}",
            stock.audioTracksInitialisedAtJoin,
            overridden.audioTracksInitialisedAtJoin,
        )
    }

    /**
     * Whether a stock player joins two identical items without rebuilding its output.
     *
     * If this passes while the overridden sink does not, the override is the defect
     * and PlaybackService has the same one. If it fails too, gapless is not available
     * from this Media3 configuration at all and the app was never going to have it.
     */
    @Test
    fun theStockSinkKeepsItsAudioTrackAcrossAJoin() {
        val stock = measureJoin(sink = SinkUnderTest.Stock)

        assertTrue("no join was observed", stock.joinObserved)
        assertEquals(
            "the stock sink created a new AudioTrack at a join between identical items",
            0,
            stock.audioTracksInitialisedAtJoin,
        )
    }

    private enum class SinkUnderTest { Stock, OverriddenBare, OverriddenWithDsp }

    /**
     * Plays the same file twice and counts what happens across the boundary.
     *
     * Counts are sampled once the first item is established and again once the second
     * is, so start-up costs are excluded and only the join is measured.
     */
    private fun measureJoin(
        withDsp: Boolean = false,
        floatOutput: Boolean = true,
        sink: SinkUnderTest = if (withDsp) SinkUnderTest.OverriddenWithDsp else SinkUnderTest.OverriddenBare,
    ): JoinMeasurement {
        val recorder = RecordingAudioProcessor()
        val audioTracks = AudioTrackCounter()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var player: ExoPlayer

        instrumentation.runOnMainSync {
            player = when (sink) {
                SinkUnderTest.Stock -> ExoPlayer.Builder(context).build()
                SinkUnderTest.OverriddenBare -> buildPlayer(recorder, withDsp = false, floatOutput)
                SinkUnderTest.OverriddenWithDsp -> buildPlayer(recorder, withDsp = true, floatOutput)
            }
            player.addAnalyticsListener(audioTracks)
            player.setMediaItems(listOf(mediaItem(), mediaItem()))
            player.prepare()
            player.volume = 0f
            player.play()
        }

        val reachedFirst = awaitItem(player, index = 0)
        val flushesBefore = recorder.flushCount
        val configuresBefore = recorder.configureCount
        val audioTracksBefore = audioTracks.initialisedCount

        val reachedSecond = awaitItem(player, index = 1)
        val flushesAfter = recorder.flushCount
        val configuresAfter = recorder.configureCount
        val audioTracksAfter = audioTracks.initialisedCount

        instrumentation.runOnMainSync { player.release() }

        return JoinMeasurement(
            joinObserved = reachedFirst && reachedSecond,
            flushesAtJoin = flushesAfter - flushesBefore,
            configuresAtJoin = configuresAfter - configuresBefore,
            audioTracksInitialisedAtJoin = audioTracksAfter - audioTracksBefore,
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
