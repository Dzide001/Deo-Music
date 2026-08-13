// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalChainTest {

    private fun stages(
        gainDb: Double = 0.0,
        bands: List<EqBand> = emptyList(),
        limiterEnabled: Boolean = true,
        reductionDb: Double = 0.0,
    ) = SignalChainStages.from(
        AudioChainConfig(gainDb = gainDb, bands = bands, limiterEnabled = limiterEnabled),
        limiterReductionDb = reductionDb,
    )

    // ---- formats ------------------------------------------------------------

    @Test
    fun `a format reads as codec, rate, depth and channels`() {
        val format = StreamFormat(
            codec = "FLAC",
            sampleRateHz = 44_100,
            channelCount = 2,
            bitDepth = 16,
        )

        assertEquals("FLAC · 44.1 kHz · 16-bit · stereo", format.describe())
    }

    @Test
    fun `a whole-number rate loses its decimal`() {
        assertEquals("48 kHz", formatSampleRate(48_000))
        assertEquals("96 kHz", formatSampleRate(96_000))
        assertEquals("44.1 kHz", formatSampleRate(44_100))
    }

    /**
     * A compressed source has no bit depth. Reporting one is how a player ends up
     * telling a listener their MP3 is 16-bit.
     */
    @Test
    fun `a source with no bit depth does not invent one`() {
        val format = StreamFormat(codec = "MP3", sampleRateHz = 44_100, channelCount = 2, bitrateKbps = 320)

        assertEquals("MP3 · 44.1 kHz · stereo · 320 kbps", format.describe())
        assertFalse(format.describe().contains("bit"))
    }

    @Test
    fun `channel counts read as names where they have them`() {
        assertEquals("mono", describeChannels(1))
        assertEquals("stereo", describeChannels(2))
        assertEquals("5.1", describeChannels(6))
        assertEquals("7.1", describeChannels(8))
        assertEquals("3 channels", describeChannels(3))
    }

    @Test
    fun `a format with nothing known says so rather than rendering empty`() {
        assertEquals("unknown", StreamFormat().describe())
    }

    @Test
    fun `a zero bitrate is omitted rather than shown as zero`() {
        val format = StreamFormat(codec = "AAC", sampleRateHz = 44_100, bitrateKbps = 0)

        assertFalse(format.describe(), format.describe().contains("0 kbps"))
    }

    /**
     * The subtype of an MP3's MIME type is "mpeg" and of AAC's is "mp4a-latm".
     * Upper-casing those and calling it the codec is accurate and unhelpful.
     */
    @Test
    fun `codecs read as the names people know them by`() {
        assertEquals("MP3", codecLabelFor("audio/mpeg"))
        assertEquals("AAC", codecLabelFor("audio/mp4a-latm"))
        assertEquals("FLAC", codecLabelFor("audio/flac"))
        assertEquals("Opus", codecLabelFor("audio/opus"))
        assertEquals("PCM", codecLabelFor("audio/raw"))
    }

    @Test
    fun `an unmapped codec falls back to its subtype rather than to nothing`() {
        assertEquals("APE", codecLabelFor("audio/ape"))
    }

    @Test
    fun `an absent or blank mime type has no label`() {
        assertEquals(null, codecLabelFor(null))
        assertEquals(null, codecLabelFor("  "))
    }

    // ---- stages -------------------------------------------------------------

    /**
     * Every stage is listed whether or not it is doing anything. A stage missing from
     * the list cannot be told apart from one that failed to load.
     */
    @Test
    fun `all three stages are always present and in order`() {
        val names = stages().map { it.name }

        assertEquals(listOf("ReplayGain", "Equaliser", "Limiter"), names)
    }

    @Test
    fun `gain reports its adjustment and whether it is doing anything`() {
        val none = stages(gainDb = 0.0).first()
        assertEquals("no adjustment", none.detail)
        assertFalse(none.active)

        val cut = stages(gainDb = -6.5).first()
        assertEquals("-6.5 dB", cut.detail)
        assertTrue(cut.active)

        assertEquals("+3.0 dB", stages(gainDb = 3.0).first().detail)
    }

    @Test
    fun `the equaliser names a single band and counts several`() {
        val flat = stages().first { it.name == "Equaliser" }
        assertEquals("flat", flat.detail)
        assertFalse(flat.active)

        val one = stages(bands = listOf(EqBand(EqBandType.Peaking, 3000.0, gainDb = 4.0)))
            .first { it.name == "Equaliser" }
        assertEquals("+4.0 dB at 3.0 kHz", one.detail)
        assertTrue(one.active)

        val several = stages(
            bands = listOf(
                EqBand(EqBandType.Peaking, 100.0, gainDb = 2.0),
                EqBand(EqBandType.Peaking, 3000.0, gainDb = -3.0),
            ),
        ).first { it.name == "Equaliser" }
        assertEquals("2 bands", several.detail)
    }

    /** Bands set to nothing are not counted, so ten flat sliders read as flat. */
    @Test
    fun `transparent bands do not count towards the equaliser`() {
        val eq = stages(bands = EqBand.fromGraphicLevels(List(10) { 0 }))
            .first { it.name == "Equaliser" }

        assertEquals("flat", eq.detail)
        assertFalse(eq.active)
    }

    /**
     * In the path and not needing to act is the normal case, and is different from
     * being switched off — which is the distinction a listener opens this to check.
     */
    @Test
    fun `the limiter separates being idle from being off`() {
        val idle = stages(limiterEnabled = true, reductionDb = 0.0).last()
        assertEquals("no reduction", idle.detail)
        assertTrue(idle.active)

        val off = stages(limiterEnabled = false).last()
        assertEquals("off", off.detail)
        assertFalse(off.active)

        val working = stages(limiterEnabled = true, reductionDb = -2.4).last()
        assertEquals("peak reduction 2.4 dB", working.detail)
        assertTrue(working.active)
    }

    /** Reduction arrives as a negative number; showing "-2.4 dB reduction" reads as a boost. */
    @Test
    fun `reduction is reported as a magnitude`() {
        assertEquals("peak reduction 2.4 dB", stages(reductionDb = -2.4).last().detail)
        assertEquals("peak reduction 2.4 dB", stages(reductionDb = 2.4).last().detail)
    }

    @Test
    fun `an empty chain still describes itself`() {
        val chain = SignalChain()

        assertEquals(null, chain.source)
        assertEquals(null, chain.decoderName)
        assertTrue(chain.stages.isEmpty())
    }
}
