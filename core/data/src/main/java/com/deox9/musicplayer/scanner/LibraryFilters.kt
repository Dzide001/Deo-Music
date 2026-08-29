// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.scanner

/**
 * Which files belong in the library at all.
 *
 * A phone's audio is not all music. Voice memos, WhatsApp notes, ringtones, game
 * sounds and notification blips all land in the same MediaStore, and a player that
 * indexes them indiscriminately gives you a library you have to scroll past rather
 * than one you use.
 */
object LibraryFilters {

    /**
     * Whether a folder has been hidden.
     *
     * Matches the folder itself and everything under it, because hiding
     * `/storage/emulated/0/WhatsApp` and still getting
     * `/storage/emulated/0/WhatsApp/Media/Audio` would not be hiding it at all.
     *
     * Compared case-insensitively: the same directory arrives with different casing
     * from MediaStore and from a document tree, and a blacklist that depends on
     * which one wrote it is a blacklist that stops working at random.
     */
    fun isHidden(folderPath: String?, hiddenFolders: Set<String>): Boolean {
        val path = folderPath?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return false
        return hiddenFolders.any { hidden ->
            val prefix = hidden.trim().trimEnd('/').takeIf { it.isNotEmpty() } ?: return@any false
            path.equals(prefix, ignoreCase = true) ||
                // The separator matters: without it, hiding /music/live would also
                // hide /music/livestreams, which shares a prefix and nothing else.
                path.startsWith("$prefix/", ignoreCase = true)
        }
    }

    /**
     * Whether a track is long enough to be worth indexing.
     *
     * Zero means no filter. The reason this is not simply "over a minute" is that
     * short tracks are real — interludes, skits, intros on hip-hop records — so the
     * threshold belongs to the person whose library it is.
     */
    fun isLongEnough(durationMs: Long, minimumDurationMs: Long): Boolean =
        minimumDurationMs <= 0L || durationMs >= minimumDurationMs

    /** Everything that survives both filters. */
    fun apply(
        tracks: List<ScannedTrack>,
        hiddenFolders: Set<String>,
        minimumDurationMs: Long,
    ): List<ScannedTrack> = tracks.filter { track ->
        !isHidden(track.folderPath, hiddenFolders) &&
            isLongEnough(track.durationMs, minimumDurationMs)
    }
}
