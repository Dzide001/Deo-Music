// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.ui

import java.text.Normalizer
import java.util.Locale

/**
 * One entry in the fast-scroll rail: a letter and the list index it jumps to.
 */
data class AlphabetBucket(
    val label: Char,
    val firstIndex: Int,
)

/** Buckets everything that is not a Latin letter, digits included. */
const val OTHER_BUCKET = '#'

/** Digits share one bucket; a rail with 0-9 plus A-Z does not fit a phone edge. */
private const val DIGIT_BUCKET = '0'

private val COMBINING_MARKS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

/**
 * Builds the fast-scroll rail for an already-sorted list of labels.
 *
 * Only meaningful when the list is sorted the same way the rail implies, so callers
 * pass the labels in display order and the result is buckets in that same order —
 * this does no sorting of its own. A list sorted by duration gets a rail whose
 * letters run backwards, which is why [shouldShowFastScroll] gates on the sort too.
 *
 * Labels are folded the same way [com.deox9.musicplayer.scanner.SortKeys] folds sort
 * keys, so "Ólafur" lands under O rather than in the catch-all — otherwise the rail
 * would disagree with the order of the list it is scrolling.
 */
fun buildAlphabetIndex(labels: List<String>): List<AlphabetBucket> {
    val buckets = mutableListOf<AlphabetBucket>()
    labels.forEachIndexed { index, label ->
        val bucket = bucketFor(label)
        // Only the first occurrence matters; a later run of the same letter is the
        // same bucket, and re-entering one means the list was not sorted.
        if (buckets.none { it.label == bucket }) {
            buckets += AlphabetBucket(bucket, index)
        }
    }
    return buckets
}

/**
 * The rail letter a label files under.
 *
 * Leading articles are stripped to match the stored sort keys: "The Beatles" is
 * under B in the list, so a rail that put it under T would scroll to the wrong place.
 */
fun bucketFor(label: String): Char {
    val trimmed = label.trim().lowercase(Locale.ROOT)
    if (trimmed.isEmpty()) return OTHER_BUCKET

    val withoutArticle = LEADING_ARTICLES
        .firstOrNull { trimmed.startsWith(it) }
        ?.let { trimmed.removePrefix(it).trimStart() }
        ?: trimmed

    val first = foldDiacritics(withoutArticle).firstOrNull() ?: return OTHER_BUCKET
    return when {
        first in 'a'..'z' -> first.uppercaseChar()
        first.isDigit() -> DIGIT_BUCKET
        else -> OTHER_BUCKET
    }
}

/**
 * Thins the rail down to what fits, keeping the ends.
 *
 * A full A-Z plus digits and the catch-all is 28 labels, which does not fit the
 * height of a phone list; drawn anyway it silently clips the last few, so the rail
 * stops at S and the letters below it look missing rather than omitted.
 *
 * Only the *labels* are thinned. Position still maps across every bucket, so
 * dropping a letter costs nothing but the printed hint.
 */
fun sampleBuckets(buckets: List<AlphabetBucket>, max: Int): List<AlphabetBucket> {
    if (max <= 0) return emptyList()
    if (buckets.size <= max) return buckets
    if (max == 1) return listOf(buckets.first())
    // Spread across the whole range inclusive of both ends, so the rail's first and
    // last printed letters are the list's real first and last.
    return (0 until max).map { buckets[it * (buckets.size - 1) / (max - 1)] }.distinct()
}

/**
 * Whether a list is worth putting a rail on.
 *
 * Two conditions, and both matter. A short list is faster to flick through than to
 * aim at a 20dp letter, and a rail over a list sorted by anything other than the
 * label lies about where it will land.
 */
fun shouldShowFastScroll(itemCount: Int, alphabetical: Boolean): Boolean =
    alphabetical && itemCount >= FAST_SCROLL_MIN_ITEMS

/** Below this a rail is more chrome than help. */
const val FAST_SCROLL_MIN_ITEMS = 30

private val LEADING_ARTICLES = listOf("the ", "a ", "an ")

/**
 * Strips accents, then recomposes. The recomposition is not cosmetic: NFD splits
 * Hangul syllables into conjoining Jamo, which are not combining marks and so
 * survive the strip as a different string.
 */
private fun foldDiacritics(value: String): String {
    val stripped = Normalizer.normalize(value, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    return Normalizer.normalize(stripped, Normalizer.Form.NFC)
}
