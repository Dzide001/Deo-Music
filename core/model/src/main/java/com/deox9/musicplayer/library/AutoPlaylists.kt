// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

/** Which generated list this is. */
enum class AutoPlaylistKind {
    MostPlayed,
    RecentlyAdded,
    ;

    val displayName: String
        get() = when (this) {
            MostPlayed -> "Most played"
            RecentlyAdded -> "Recently added"
        }
}

/**
 * A playlist the app works out rather than one someone made.
 *
 * Held as a value with its tracks already resolved, so the screen showing it does
 * not need to know the difference between this and a real playlist — and so the rule
 * that produced it can be tested without a database.
 */
data class AutoPlaylist(
    val kind: AutoPlaylistKind,
    val tracks: List<LocalTrack>,
) {
    val name: String get() = kind.displayName
    val trackCount: Int get() = tracks.size
}

/**
 * The generated playlists, given the library and what has been played.
 *
 * Both are derived from data the app already has — play counts collected for the
 * Suggested tab, and the date MediaStore recorded when each file appeared — so
 * nothing new is stored or tracked to produce them.
 *
 * An empty one is omitted rather than shown empty. A fresh install would otherwise
 * offer two playlists that open onto nothing, which reads as the feature being
 * broken rather than as the library being new.
 */
fun autoPlaylists(
    tracks: List<LocalTrack>,
    playCounts: Map<String, Int>,
    limit: Int = DEFAULT_LIMIT,
): List<AutoPlaylist> {
    if (tracks.isEmpty()) return emptyList()

    val mostPlayed = tracks
        .asSequence()
        .mapNotNull { track ->
            val plays = playCounts[track.contentUri] ?: 0
            if (plays > 0) track to plays else null
        }
        // Ties break on title so the order is stable between openings. Without it two
        // tracks played the same number of times swap places on every visit, which
        // looks like the counts are moving when nothing has happened.
        .sortedWith(compareByDescending<Pair<LocalTrack, Int>> { it.second }.thenBy { it.first.title })
        .map { it.first }
        .take(limit)
        .toList()

    val recentlyAdded = tracks
        .asSequence()
        // Zero is "MediaStore did not say", not the epoch. Treating it as a date puts
        // every untimestamped file at the bottom of a list sorted by date, which is
        // harmless, and at the top of one sorted the other way, which is not — so it
        // is excluded rather than ordered.
        .filter { it.dateAddedMs > 0 }
        .sortedWith(compareByDescending<LocalTrack> { it.dateAddedMs }.thenBy { it.title })
        .take(limit)
        .toList()

    return buildList {
        if (mostPlayed.isNotEmpty()) add(AutoPlaylist(AutoPlaylistKind.MostPlayed, mostPlayed))
        if (recentlyAdded.isNotEmpty()) add(AutoPlaylist(AutoPlaylistKind.RecentlyAdded, recentlyAdded))
    }
}

/**
 * Long enough to be a listening session, short enough to stay a highlight.
 *
 * Past this "Most played" stops meaning anything — a hundred entries is just the
 * library again, in a different order.
 */
private const val DEFAULT_LIMIT = 50
