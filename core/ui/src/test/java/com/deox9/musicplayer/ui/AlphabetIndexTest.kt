// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetIndexTest {

    @Test
    fun `buckets point at the first item with that letter`() {
        val index = buildAlphabetIndex(listOf("Abbey Road", "Achtung Baby", "Blue", "Curtis"))

        assertEquals(
            listOf(
                AlphabetBucket('A', 0),
                AlphabetBucket('B', 2),
                AlphabetBucket('C', 3),
            ),
            index,
        )
    }

    @Test
    fun `leading articles file under the following word`() {
        assertEquals('B', bucketFor("The Beatles"))
        assertEquals('W', bucketFor("A Wall"))
        assertEquals('E', bucketFor("An Evening"))
    }

    /** "A" alone is a title, not an article, so it must not strip to nothing. */
    @Test
    fun `a bare article is still a label`() {
        assertEquals('A', bucketFor("A"))
        assertEquals('T', bucketFor("The"))
    }

    @Test
    fun `accents fold onto the base letter`() {
        assertEquals('O', bucketFor("Ólafur Arnalds"))
        assertEquals('E', bucketFor("Étienne"))
    }

    @Test
    fun `digits share one bucket and everything else falls through`() {
        assertEquals('0', bucketFor("1979"))
        assertEquals('0', bucketFor("99 Luftballons"))
        assertEquals(OTHER_BUCKET, bucketFor("김광석"))
        assertEquals(OTHER_BUCKET, bucketFor("!!!"))
        assertEquals(OTHER_BUCKET, bucketFor("   "))
        assertEquals(OTHER_BUCKET, bucketFor(""))
    }

    /**
     * The rail is built from display order, so a bucket must never be entered twice.
     * If it were, tapping the letter would scroll to whichever occurrence won.
     */
    @Test
    fun `a repeated letter keeps only its first index`() {
        val index = buildAlphabetIndex(listOf("Alpha", "Beta", "Anchor"))

        assertEquals(listOf(AlphabetBucket('A', 0), AlphabetBucket('B', 1)), index)
    }

    @Test
    fun `empty input has no buckets`() {
        assertEquals(emptyList<AlphabetBucket>(), buildAlphabetIndex(emptyList()))
    }

    @Test
    fun `sampling keeps both ends and never grows the list`() {
        val buckets = ('A'..'Z').mapIndexed { i, c -> AlphabetBucket(c, i) }

        val sampled = sampleBuckets(buckets, max = 10)

        assertEquals(10, sampled.size)
        assertEquals(buckets.first(), sampled.first())
        assertEquals(buckets.last(), sampled.last())
        // Order must survive, or the rail would print letters out of sequence.
        assertEquals(sampled.sortedBy { it.firstIndex }, sampled)
    }

    @Test
    fun `sampling leaves a list that already fits alone`() {
        val buckets = listOf(AlphabetBucket('A', 0), AlphabetBucket('B', 4))

        assertEquals(buckets, sampleBuckets(buckets, max = 10))
        assertEquals(buckets, sampleBuckets(buckets, max = 2))
    }

    @Test
    fun `sampling degenerate limits does not crash`() {
        val buckets = listOf(AlphabetBucket('A', 0), AlphabetBucket('B', 4), AlphabetBucket('C', 9))

        assertEquals(emptyList<AlphabetBucket>(), sampleBuckets(buckets, max = 0))
        assertEquals(listOf(buckets.first()), sampleBuckets(buckets, max = 1))
    }

    @Test
    fun `the rail is hidden on short lists and on non-alphabetical sorts`() {
        assertTrue(shouldShowFastScroll(itemCount = 400, alphabetical = true))
        assertFalse(shouldShowFastScroll(itemCount = 400, alphabetical = false))
        assertFalse(shouldShowFastScroll(itemCount = 5, alphabetical = true))
    }
}
