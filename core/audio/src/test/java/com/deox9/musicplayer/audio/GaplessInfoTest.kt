// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GaplessInfoTest {

    // ---- Xing / LAME --------------------------------------------------------

    /**
     * Builds a first frame carrying a Xing or Info tag.
     *
     * The optional-field flags matter more than they look: the table of contents is
     * 100 bytes, so getting the flags wrong puts the delay read that far out and
     * yields a plausible-looking but entirely wrong number.
     */
    private fun frameWith(
        magic: String,
        delay: Int,
        padding: Int,
        includeFrames: Boolean = true,
        includeBytes: Boolean = true,
        includeToc: Boolean = true,
        includeQuality: Boolean = false,
    ): ByteArray {
        val out = ArrayList<Byte>()
        // A plausible MPEG1 Layer III header and the side info before the tag.
        repeat(36) { out.add(0) }
        out[0] = 0xFF.toByte()
        out[1] = 0xFB.toByte()

        magic.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }

        var flags = 0
        if (includeFrames) flags = flags or 0x0001
        if (includeBytes) flags = flags or 0x0002
        if (includeToc) flags = flags or 0x0004
        if (includeQuality) flags = flags or 0x0008
        out.add((flags shr 24).toByte())
        out.add((flags shr 16).toByte())
        out.add((flags shr 8).toByte())
        out.add(flags.toByte())

        if (includeFrames) repeat(4) { out.add(0) }
        if (includeBytes) repeat(4) { out.add(0) }
        if (includeToc) repeat(100) { out.add(0) }
        if (includeQuality) repeat(4) { out.add(0) }

        // The LAME block: nine bytes of name, then fixed fields to offset 21.
        val lame = ByteArray(36)
        "LAME3.100".toByteArray(Charsets.US_ASCII).copyInto(lame)
        lame[21] = (delay shr 4).toByte()
        lame[22] = (((delay and 0x0F) shl 4) or ((padding shr 8) and 0x0F)).toByte()
        lame[23] = (padding and 0xFF).toByte()
        lame.forEach { out.add(it) }

        return out.toByteArray()
    }

    @Test
    fun `a Xing tag yields its delay and padding`() {
        val info = XingLameHeader.parse(frameWith("Xing", delay = 576, padding = 1908))

        assertNotNull(info)
        assertEquals(576, info!!.encoderDelayFrames)
        assertEquals(1908, info.encoderPaddingFrames)
    }

    /**
     * "Info" is what LAME writes for a constant-bitrate file. A parser that looks for
     * "Xing" alone silently misses every CBR file it has ever produced.
     */
    @Test
    fun `an Info tag is read the same way as a Xing tag`() {
        val info = XingLameHeader.parse(frameWith("Info", delay = 1104, padding = 288))

        assertEquals(1104, info!!.encoderDelayFrames)
        assertEquals(288, info.encoderPaddingFrames)
    }

    /** Each optional field shifts the LAME block; the flags are the only guide. */
    @Test
    fun `the optional fields are skipped by their flags, not assumed`() {
        val combinations = listOf(
            Triple(true, true, true),
            Triple(false, false, false),
            Triple(true, false, true),
            Triple(false, true, false),
        )

        combinations.forEach { (frames, bytes, toc) ->
            val info = XingLameHeader.parse(
                frameWith("Xing", delay = 576, padding = 1000, includeFrames = frames, includeBytes = bytes, includeToc = toc),
            )
            assertEquals("frames=$frames bytes=$bytes toc=$toc", 576, info?.encoderDelayFrames)
            assertEquals(1000, info?.encoderPaddingFrames)
        }
    }

    @Test
    fun `the quality field is skipped when present`() {
        val info = XingLameHeader.parse(
            frameWith("Xing", delay = 576, padding = 1152, includeQuality = true),
        )

        assertEquals(576, info!!.encoderDelayFrames)
        assertEquals(1152, info.encoderPaddingFrames)
    }

    @Test
    fun `a frame with no tag at all yields nothing`() {
        assertNull(XingLameHeader.parse(ByteArray(400)))
    }

    /**
     * Truncated past the delay field, specifically. Cutting only the LAME block's
     * tail still parses, correctly — those bytes are fields nothing here reads, and
     * rejecting the file for their absence would lose a usable tag.
     */
    @Test
    fun `a frame ending before the delay field is rejected`() {
        val full = frameWith("Xing", delay = 576, padding = 1908)

        // The delay sits at the last 15 bytes of the block; end the frame before it.
        assertNull(XingLameHeader.parse(full.copyOf(full.size - 15)))
        assertNull(XingLameHeader.parse(ByteArray(10)))
    }

    @Test
    fun `losing only the trailing LAME fields still yields the delay`() {
        val full = frameWith("Xing", delay = 576, padding = 1908)

        val info = XingLameHeader.parse(full.copyOf(full.size - 4))

        assertEquals(576, info!!.encoderDelayFrames)
    }

    /**
     * Both fields zero means the encoder recorded nothing to trim, which is not the
     * same as a delay worth acting on.
     */
    @Test
    fun `an all-zero tag is not treated as usable`() {
        assertNull(XingLameHeader.parse(frameWith("Xing", delay = 0, padding = 0)))
    }

    /** Trimming a second off the front is a corrupt tag, not an unusual one. */
    @Test
    fun `an implausibly large delay is rejected`() {
        val info = GaplessInfo(encoderDelayFrames = 500_000, encoderPaddingFrames = 0)

        assertFalse(info.isUsable)
    }

    @Test
    fun `the twelve-bit fields hold their maximum values`() {
        val info = XingLameHeader.parse(frameWith("Xing", delay = 4095, padding = 4095))

        assertEquals(4095, info!!.encoderDelayFrames)
        assertEquals(4095, info.encoderPaddingFrames)
    }

    // ---- iTunSMPB -----------------------------------------------------------

    @Test
    fun `an iTunSMPB value yields its priming and remainder`() {
        val raw = " 00000000 00000840 000001E0 0000000000B7EBE0 00000000 00000000 " +
            "00000000 00000000 00000000 00000000 00000000 00000000"

        val info = ITunSmpb.parse(raw)

        assertEquals(0x840, info!!.encoderDelayFrames)
        assertEquals(0x1E0, info.encoderPaddingFrames)
    }

    @Test
    fun `leading and trailing whitespace does not matter`() {
        val info = ITunSmpb.parse("\n  00000000 00000840 000001E0 0000000000B7EBE0  \t")

        assertEquals(0x840, info!!.encoderDelayFrames)
    }

    @Test
    fun `a value with too few fields is not salvaged`() {
        assertNull(ITunSmpb.parse("00000000 00000840"))
        assertNull(ITunSmpb.parse(""))
        assertNull(ITunSmpb.parse(null))
    }

    @Test
    fun `a value that is not hex is rejected`() {
        assertNull(ITunSmpb.parse("00000000 nonsense 000001E0 0000000000B7EBE0"))
    }

    @Test
    fun `an iTunSMPB with nothing to trim is not usable`() {
        assertNull(ITunSmpb.parse("00000000 00000000 00000000 0000000000B7EBE0"))
    }

    @Test
    fun `padding alone is enough to be worth acting on`() {
        val info = GaplessInfo(encoderDelayFrames = 0, encoderPaddingFrames = 1152)

        assertTrue(info.isUsable)
    }
}
