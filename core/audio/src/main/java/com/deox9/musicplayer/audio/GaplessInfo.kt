// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.audio

/**
 * The samples an encoder added that were never in the recording.
 *
 * Both MP3 and AAC are block-based: the encoder cannot start mid-block, so it pads
 * the front with silence to fill the first one and the back to fill the last. Play
 * the file as decoded and you hear both — a few tens of milliseconds of nothing at
 * each end. On separate songs that is inaudible. Across a continuous mix or a live
 * album it is the gap that gapless playback exists to remove.
 *
 * Counts are in samples, not milliseconds, because that is what the decoder trims in
 * and what the tags store. Converting to time first loses the exactness that makes
 * the join seamless rather than merely short.
 */
data class GaplessInfo(
    val encoderDelayFrames: Int,
    val encoderPaddingFrames: Int,
) {
    val isUsable: Boolean
        get() = encoderDelayFrames >= 0 &&
            encoderPaddingFrames >= 0 &&
            (encoderDelayFrames > 0 || encoderPaddingFrames > 0) &&
            encoderDelayFrames <= MAX_PLAUSIBLE_FRAMES &&
            encoderPaddingFrames <= MAX_PLAUSIBLE_FRAMES

    companion object {
        /**
         * Beyond about a second of trim, the tag is wrong rather than unusual.
         *
         * A corrupt value here does not fail loudly — it silently removes audio from
         * the start of the track, which is far worse than leaving the gap in.
         */
        const val MAX_PLAUSIBLE_FRAMES = 100_000
    }
}

/**
 * The LAME/Xing tag carried in an MP3's first frame.
 *
 * The frame is a real MP3 frame that decodes to silence, so a player that knows
 * nothing about it just plays the silence — which is exactly the gap. The delay and
 * padding sit in three bytes, packed as two twelve-bit fields.
 */
object XingLameHeader {

    /**
     * Reads the delay and padding out of the first frame of an MP3.
     *
     * [frame] should start at the MPEG sync word. Returns null when the frame carries
     * no Xing/Info tag at all, which is the common case for a stream that was never
     * encoded by LAME.
     */
    @Suppress("ReturnCount")
    fun parse(frame: ByteArray): GaplessInfo? {
        val tagOffset = findXingOffset(frame) ?: return null

        // Four flag bits say which optional fields follow the magic; each has to be
        // skipped to find the LAME block, and guessing their presence puts the read
        // 100 bytes out because of the table of contents.
        var offset = tagOffset + MAGIC_LENGTH
        if (offset + FLAGS_LENGTH > frame.size) return null
        val flags = readInt(frame, offset)
        offset += FLAGS_LENGTH

        if (flags and FLAG_FRAMES != 0) offset += FIELD_LENGTH
        if (flags and FLAG_BYTES != 0) offset += FIELD_LENGTH
        if (flags and FLAG_TOC != 0) offset += TOC_LENGTH
        if (flags and FLAG_QUALITY != 0) offset += FIELD_LENGTH

        // The LAME extension follows: nine bytes of encoder name, then fixed fields,
        // with the delays at offset 21.
        val delayOffset = offset + LAME_DELAY_OFFSET
        if (delayOffset + DELAY_FIELD_LENGTH > frame.size) return null

        val first = frame[delayOffset].toInt() and BYTE_MASK
        val middle = frame[delayOffset + 1].toInt() and BYTE_MASK
        val last = frame[delayOffset + 2].toInt() and BYTE_MASK

        val delay = (first shl NIBBLE_BITS) or (middle shr NIBBLE_BITS)
        val padding = ((middle and LOW_NIBBLE) shl BYTE_BITS) or last

        val info = GaplessInfo(encoderDelayFrames = delay, encoderPaddingFrames = padding)
        return info.takeIf { it.isUsable }
    }

    /**
     * Finds "Xing" or "Info" within the frame.
     *
     * Both are searched for: "Xing" marks a variable bitrate file and "Info" a
     * constant one, and only the first is widely known — a player that looks for
     * "Xing" alone misses every CBR file LAME ever produced.
     */
    private fun findXingOffset(frame: ByteArray): Int? {
        val limit = minOf(frame.size, SEARCH_LIMIT) - MAGIC_LENGTH
        for (index in 0..limit) {
            if (matches(frame, index, XING) || matches(frame, index, INFO)) return index
        }
        return null
    }

    private fun matches(frame: ByteArray, offset: Int, magic: ByteArray): Boolean {
        for (index in magic.indices) {
            if (frame[offset + index] != magic[index]) return false
        }
        return true
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and BYTE_MASK) shl 24) or
            ((bytes[offset + 1].toInt() and BYTE_MASK) shl 16) or
            ((bytes[offset + 2].toInt() and BYTE_MASK) shl 8) or
            (bytes[offset + 3].toInt() and BYTE_MASK)

    private val XING = "Xing".toByteArray(Charsets.US_ASCII)
    private val INFO = "Info".toByteArray(Charsets.US_ASCII)

    private const val MAGIC_LENGTH = 4
    private const val FLAGS_LENGTH = 4
    private const val FIELD_LENGTH = 4
    private const val TOC_LENGTH = 100
    private const val LAME_DELAY_OFFSET = 21
    private const val DELAY_FIELD_LENGTH = 3

    private const val FLAG_FRAMES = 0x0001
    private const val FLAG_BYTES = 0x0002
    private const val FLAG_TOC = 0x0004
    private const val FLAG_QUALITY = 0x0008

    /** The tag always sits within the first frame; beyond this it is audio. */
    private const val SEARCH_LIMIT = 200

    private const val BYTE_MASK = 0xFF
    private const val LOW_NIBBLE = 0x0F
    private const val NIBBLE_BITS = 4
    private const val BYTE_BITS = 8
}

/**
 * Apple's equivalent, stored as text.
 *
 * A run of space-separated hex fields written by iTunes and by anything imitating it.
 * The interesting three are the priming, the remainder and the original sample count;
 * the rest describe the file in ways nothing here needs.
 */
object ITunSmpb {

    /**
     * Parses an iTunSMPB value.
     *
     * The leading field is always zero and the useful ones follow it, so a value with
     * fewer than four fields is not a truncated tag to salvage — it is a different
     * thing that happens to be in the same place.
     */
    fun parse(raw: String?): GaplessInfo? {
        val fields = raw?.trim()?.split(WHITESPACE)?.filter { it.isNotEmpty() } ?: return null
        if (fields.size < MINIMUM_FIELDS) return null

        val delay = fields[PRIMING_INDEX].toIntOrNull(HEX) ?: return null
        val padding = fields[REMAINDER_INDEX].toIntOrNull(HEX) ?: return null

        val info = GaplessInfo(encoderDelayFrames = delay, encoderPaddingFrames = padding)
        return info.takeIf { it.isUsable }
    }

    private val WHITESPACE = "\\s+".toRegex()
    private const val MINIMUM_FIELDS = 4
    private const val PRIMING_INDEX = 1
    private const val REMAINDER_INDEX = 2
    private const val HEX = 16
}
