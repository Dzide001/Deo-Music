// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

/**
 * Star ratings, and the ID3 byte they are stored as.
 *
 * ID3v2 keeps a rating in a POPM ("popularimeter") frame as a single byte from 0 to
 * 255, and the spec says nothing whatsoever about how that maps to stars. What
 * exists instead is a convention, set by Windows Media Player and followed by
 * MediaMonkey, foobar2000, MusicBee and most of the Android players people migrate
 * from: 1, 64, 128, 196, 255 for one through five stars.
 *
 * Those five values are what this writes. Reading has to be more forgiving, because
 * other software writes other numbers — some spread ratings linearly across the
 * whole range, some write 20/40/60/80/100 — so a read takes the nearest band rather
 * than looking for an exact match. Getting this wrong is quiet and annoying: a
 * library rated elsewhere comes back with every track a star or two out, which looks
 * like the ratings were lost rather than misread.
 *
 * 0 means unrated, not "zero stars". There is no way to say "I have heard this and
 * it is worthless" in POPM, and inventing one would write a value other software
 * reads as one star.
 */
object Popularimeter {

    /** Stars, 0 (unrated) through 5. */
    const val MAX_STARS = 5

    /**
     * The byte to write for a star rating.
     *
     * One star is 1 rather than 51 or any other even division, because that is what
     * Windows Media Player writes and everything else reads. A linear mapping would
     * be tidier and would be misread as two stars by most of the software anyone is
     * likely to open the file in.
     */
    fun toPopm(stars: Int): Int = when (stars.coerceIn(0, MAX_STARS)) {
        1 -> ONE_STAR
        2 -> TWO_STARS
        3 -> THREE_STARS
        4 -> FOUR_STARS
        MAX_STARS -> FIVE_STARS
        else -> UNRATED
    }

    /**
     * The star rating a stored byte means.
     *
     * Banded rather than matched exactly, so a file rated by software using a
     * different scale still reads as something sensible. The bands are centred on
     * the Windows Media Player values, which is what makes the round trip exact for
     * anything this app wrote.
     */
    fun toStars(popm: Int): Int = when {
        popm <= UNRATED -> 0
        popm < TWO_STARS_FLOOR -> 1
        popm < THREE_STARS_FLOOR -> 2
        popm < FOUR_STARS_FLOOR -> 3
        popm < FIVE_STARS_FLOOR -> 4
        else -> MAX_STARS
    }

    /**
     * Reads a rating out of whatever a tag library handed back.
     *
     * The value arrives as text, and not always as a POPM byte: Vorbis comments and
     * MP4 both carry ratings out of 100, so a FLAC rated three stars reads as "60"
     * where an MP3 reads as "128". A bare number over five and under or equal to 100
     * that is not a value any POPM writer uses is treated as a percentage — which is
     * a heuristic, and is why it is confined to this one function with the reasoning
     * next to it rather than spread through the scanner.
     */
    fun parse(raw: String?): Int? {
        val number = raw?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return when {
            // Already a star count. Some taggers write 0-5 straight into the field.
            number <= MAX_STARS -> number

            // A percentage scale, unless it is one of the POPM values that happens to
            // fall inside it. 64 is two stars in POPM and would be two-thirds of a
            // star out of 100; the POPM reading wins, because this app writes POPM
            // and misreading its own files back would be the worse failure.
            number <= PERCENT_MAX && number !in POPM_VALUES ->
                ((number * MAX_STARS) + (PERCENT_MAX / 2)) / PERCENT_MAX

            else -> toStars(number)
        }
    }

    /** What to write into the tag field, or null to clear it. */
    fun format(stars: Int): String? =
        if (stars <= 0) null else toPopm(stars).toString()

    private const val UNRATED = 0
    private const val ONE_STAR = 1
    private const val TWO_STARS = 64
    private const val THREE_STARS = 128
    private const val FOUR_STARS = 196
    private const val FIVE_STARS = 255

    // Band edges, sitting between the conventional values.
    private const val TWO_STARS_FLOOR = 32
    private const val THREE_STARS_FLOOR = 96
    private const val FOUR_STARS_FLOOR = 160
    private const val FIVE_STARS_FLOOR = 224

    private const val PERCENT_MAX = 100
    private val POPM_VALUES = setOf(ONE_STAR, TWO_STARS, THREE_STARS, FOUR_STARS, FIVE_STARS)
}
