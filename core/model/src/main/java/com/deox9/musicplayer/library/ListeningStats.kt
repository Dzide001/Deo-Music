// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

/** An artist and how much they have been played. */
data class ArtistTally(
    val artist: String,
    val plays: Int,
)

/** A track and a count of something that happened to it. */
data class TrackTally(
    val track: LocalTrack,
    val count: Int,
)

/**
 * What the app has noticed, said back to the person it noticed it about.
 *
 * The counts behind this have been collected since the first version and shown to
 * nobody — they existed only to rank suggestions. Surfacing them is not a new kind of
 * data collection, and it is worth being clear about which direction that cuts: the
 * honest thing about counting what someone listens to is to let them see it.
 *
 * Everything here is computed on the device from data that never leaves it. There is
 * no history, only totals, so this can say what has been played most and cannot say
 * what was played on any particular evening. That is a deliberate limit of the
 * storage rather than a limit of the presentation.
 */
data class ListeningStats(
    val totalPlays: Int,
    val distinctTracksPlayed: Int,
    val totalSkips: Int,
    val topArtists: List<ArtistTally>,
    val topTracks: List<TrackTally>,
    val mostSkipped: List<TrackTally>,
    /** Plays counted against tracks no longer in the library. */
    val playsOfMissingTracks: Int,
) {
    val hasAnything: Boolean get() = totalPlays > 0 || totalSkips > 0

    /**
     * Skips as a share of everything started, 0-100.
     *
     * Against plays plus skips rather than plays alone, so the worst possible case
     * reads as 100% and not as something unbounded.
     */
    val skipPercentage: Int
        get() {
            val started = totalPlays + totalSkips
            return if (started == 0) 0 else (totalSkips * 100) / started
        }
}

/**
 * Builds the statistics from the raw counters and the current library.
 *
 * The library is needed because the counters key on content URIs and lowercased
 * artist names, neither of which is showable. Matching them back gives a title to
 * display — and a track that no longer matches is a track that has been deleted since
 * it was played, which is counted in the totals but has nothing to list.
 */
fun listeningStats(
    tracks: List<LocalTrack>,
    signals: RecommendationSignals,
    limit: Int = DEFAULT_TOP_N,
): ListeningStats {
    val byUri = tracks.associateBy { it.contentUri }

    // The counter lowercases artist names so that "ASAKE" and "Asake" are one artist.
    // The library still holds how the name is actually written, so take the first
    // spelling seen rather than showing someone their own music in lower case.
    val displayNames = mutableMapOf<String, String>()
    tracks.forEach { track ->
        val key = track.artist.lowercase()
        if (key.isNotBlank() && key !in displayNames) displayNames[key] = track.artist
    }

    val topArtists = signals.artistPlayCounts
        .filterValues { it > 0 }
        .map { (key, plays) -> ArtistTally(displayNames[key] ?: key, plays) }
        .sortedWith(compareByDescending<ArtistTally> { it.plays }.thenBy { it.artist })
        .take(limit)

    val totalPlays = signals.trackPlayCounts.values.sum()
    val playsOfMissingTracks = signals.trackPlayCounts
        .filterKeys { it !in byUri }
        .values
        .sum()

    return ListeningStats(
        totalPlays = totalPlays,
        distinctTracksPlayed = signals.trackPlayCounts.count { it.value > 0 },
        totalSkips = signals.trackSkipCounts.values.sum(),
        topArtists = topArtists,
        topTracks = tally(signals.trackPlayCounts, byUri, limit),
        mostSkipped = tally(signals.trackSkipCounts, byUri, limit),
        playsOfMissingTracks = playsOfMissingTracks,
    )
}

/**
 * Turns a URI-keyed counter into a listable ranking.
 *
 * Ties break on title so the order is stable between openings of the screen. Without
 * that, two tracks played the same number of times swap places on every visit, which
 * looks like the counts are moving when nothing has happened.
 */
private fun tally(
    counts: Map<String, Int>,
    byUri: Map<String, LocalTrack>,
    limit: Int,
): List<TrackTally> = counts
    .asSequence()
    .filter { it.value > 0 }
    .mapNotNull { (uri, count) -> byUri[uri]?.let { TrackTally(it, count) } }
    .sortedWith(compareByDescending<TrackTally> { it.count }.thenBy { it.track.title })
    .take(limit)
    .toList()

/** Enough to see a pattern, few enough to read without scrolling for a while. */
private const val DEFAULT_TOP_N = 10
