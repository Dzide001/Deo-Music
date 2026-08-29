// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

import java.text.Normalizer
import java.util.Locale

/**
 * Sort keys for library ordering.
 *
 * Sorting on the raw title files "The Beatles" under T and puts "Ólafur" after
 * "Zappa", which is the usual reason a library looks wrong at a glance.
 */
object SortKeys {

    /**
     * Leading articles stripped for sorting.
     *
     * English only for now. Adding other languages means knowing the tag's language,
     * which MediaStore does not carry — the alternative, stripping every language's
     * articles unconditionally, would mangle titles like the German "Die Antwoord".
     */
    private val LEADING_ARTICLES = listOf("the ", "a ", "an ")

    private val COMBINING_MARKS = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    /**
     * Builds the sort key for a title or name.
     *
     * Lower-cased, leading article moved off the front, diacritics folded so "Ólafur"
     * sorts with O, and whitespace collapsed. CJK is left intact: it has no case or
     * diacritics to fold, and folding would destroy the characters.
     */
    fun forTitle(raw: String): String {
        val trimmed = raw.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return ""

        val withoutArticle = LEADING_ARTICLES
            .firstOrNull { trimmed.startsWith(it) }
            ?.let { trimmed.removePrefix(it) }
            ?: trimmed

        return foldDiacritics(withoutArticle).replace("\\s+".toRegex(), " ").trim()
    }

    /**
     * Folds accents onto their base letters, leaving non-Latin scripts untouched.
     *
     * Re-composing with NFC afterwards is not cosmetic: NFD decomposes Hangul
     * syllables into conjoining Jamo, and those are not combining marks so they
     * survive the strip. Without the recomposition, "김광석" would be stored as
     * decomposed Jamo that no longer compares equal to the original.
     */
    fun foldDiacritics(value: String): String {
        val stripped = Normalizer.normalize(value, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        return Normalizer.normalize(stripped, Normalizer.Form.NFC)
    }
}
