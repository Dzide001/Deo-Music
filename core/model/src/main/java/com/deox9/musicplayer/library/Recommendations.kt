// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.library

/**
 * What the app has noticed about someone's listening.
 *
 * Counts and sets rather than a history: the recommender only ever asks "how often"
 * and "was this liked", and keeping totals instead of events means nothing here can
 * reconstruct when anything was played. That is deliberate — this data never leaves
 * the device, and the shape it is stored in should make it dull if it ever did.
 */
data class RecommendationSignals(
    val artistPlayCounts: Map<String, Int> = emptyMap(),
    val trackPlayCounts: Map<String, Int> = emptyMap(),
    val trackSkipCounts: Map<String, Int> = emptyMap(),
    val likedTrackUris: Set<String> = emptySet(),
    val hiddenTrackUris: Set<String> = emptySet(),
)

/**
 * What is playing now, as the recommender needs it.
 *
 * A flattened view rather than the playback state itself, so the scoring can be run
 * against a handful of strings in a test instead of against a live player.
 */
data class SuggestionSeed(
    val uri: String = "",
    val artist: String = "",
    val title: String = "",
    /** Artists from the current queue, which stand in for "recently in your ears". */
    val queueArtists: List<String> = emptyList(),
)

/** A suggested track, and the one thing to say about why it is here. */
data class SuggestedRecommendation(
    val track: LocalTrack,
    val reason: String,
    val score: Int,
)

/**
 * Ranks the library against what is playing.
 *
 * Weights, not rules: every track that survives the filters gets a score, and the
 * ordering falls out of the total. That is why a track someone skips repeatedly can
 * still appear — a large enough positive signal outweighs it — and why the list does
 * not empty out for a library with no play history at all, since [DISCOVERY_SCORE]
 * floors anything that matched nothing.
 *
 * Pure, and separated from the screen that shows it, because the interesting part is
 * arithmetic that is impossible to check by looking at it. Inside a composable, the
 * only way to find out whether skipping a track twice actually demotes it was to
 * skip a track twice on a phone.
 */
fun recommendTracks(
    tracks: List<LocalTrack>,
    seed: SuggestionSeed,
    signals: RecommendationSignals,
    favourites: Set<String>,
    /** Advances the window into the ranking, so "Refresh" gives a different set. */
    rotation: Int = 0,
    limit: Int = DEFAULT_LIMIT,
): List<SuggestedRecommendation> {
    if (tracks.isEmpty()) return emptyList()

    val currentArtist = seed.artist.lowercase()
    val recentArtists = seed.queueArtists
        .map { it.lowercase() }
        .filter { it.isNotBlank() }
        .distinct()

    val scored = tracks
        .asSequence()
        // The track currently playing is not a suggestion, and a hidden one has been
        // refused explicitly — the one signal that is an instruction rather than a hint.
        .filter { it.contentUri != seed.uri }
        .filter { it.contentUri !in signals.hiddenTrackUris }
        .map { track -> score(track, seed, currentArtist, recentArtists, signals, favourites) }
        .sortedByDescending { it.score }
        .take(limit)
        .toList()

    return rotated(scored, rotation)
}

/**
 * The window rotation behind "Refresh".
 *
 * Rotating rather than reshuffling, so the ranking is still a ranking: pressing
 * Refresh walks further down the same ordered list instead of scrambling it, and
 * pressing it enough times comes back round to the top.
 *
 * A press moves by [ROTATION_STRIDE] rather than by one, because moving by one is
 * not a refresh anyone can see. These rows are tall - artwork, title, artist, the
 * reason line and two buttons - so only three or four fit a phone screen, and
 * shifting by one leaves two thirds of what was already there. A stride wider than
 * the screen means every press replaces the visible set outright.
 */
private fun rotated(
    scored: List<SuggestedRecommendation>,
    rotation: Int,
): List<SuggestedRecommendation> {
    if (scored.isEmpty()) return scored
    // Through Long, because the stride multiplies a tap count that only ever grows.
    val shift = ((rotation.toLong() * ROTATION_STRIDE) % scored.size).toInt()
    return if (shift == 0) scored else scored.drop(shift) + scored.take(shift)
}

@Suppress("CyclomaticComplexMethod")
private fun score(
    track: LocalTrack,
    seed: SuggestionSeed,
    currentArtist: String,
    recentArtists: List<String>,
    signals: RecommendationSignals,
    favourites: Set<String>,
): SuggestedRecommendation {
    val artistLower = track.artist.lowercase()
    val titleLower = track.title.lowercase()
    val artistPlayCount = signals.artistPlayCounts[artistLower] ?: 0
    val trackPlayCount = signals.trackPlayCounts[track.contentUri] ?: 0
    val trackSkipCount = signals.trackSkipCounts[track.contentUri] ?: 0

    var score = 0
    val reasons = mutableListOf<String>()

    if (artistLower == currentArtist && currentArtist.isNotBlank()) {
        score += SAME_ARTIST
        reasons += "same artist"
    } else if (artistLower in recentArtists) {
        score += ARTIST_IN_QUEUE
        reasons += "artist in your recent queue"
    }

    if (track.contentUri in favourites) {
        score += FAVOURITE
        reasons += "you starred this"
    }

    // Weighed above a star because it is feedback on this feature specifically:
    // someone liking a suggestion is saying more suggestions like it, whereas a
    // favourite says only that they like the track.
    if (track.contentUri in signals.likedTrackUris) {
        score += LIKED_SUGGESTION
        reasons += "you liked this recommendation"
    }

    if (artistPlayCount > 0) {
        score += artistPlayCount.coerceAtMost(ARTIST_PLAYS_CAP) * ARTIST_PLAY_WEIGHT
        reasons += "frequently played artist"
    }

    // Deliberately silent: repeat plays lift a track without claiming a reason,
    // because "you have played this before" is not a recommendation.
    if (trackPlayCount > 0) {
        score += trackPlayCount.coerceAtMost(TRACK_PLAYS_CAP) * TRACK_PLAY_WEIGHT
    }

    // The only negative weight, and heavier than most positives: skipping is the
    // clearest statement anyone makes without pressing anything labelled.
    if (trackSkipCount > 0) {
        score -= trackSkipCount.coerceAtMost(TRACK_SKIPS_CAP) * TRACK_SKIP_WEIGHT
        reasons += "you often skip this"
    }

    if (seed.title.isNotBlank()) {
        val seedWord = seed.title.split(" ").firstOrNull()?.trim()?.lowercase().orEmpty()
        // Four characters up, because shorter first words are "the", "a", "my" —
        // matching those would tie together tracks with nothing in common.
        if (seedWord.length >= SEED_WORD_MINIMUM && titleLower.contains(seedWord)) {
            score += TITLE_SIMILARITY
            reasons += "title similarity"
        }
    }

    if (track.durationMs in SONG_LENGTH_MS) {
        score += SONG_LENGTH
    }

    // Nothing matched. A small positive rather than zero keeps the track in the list
    // ordered below everything that did match, which is what makes suggestions work
    // at all on a library the app knows nothing about yet.
    //
    // Note this is `== 0` and not `<= 0`: a track pushed negative by skips has
    // matched something, and floats back up only if a positive signal outweighs it.
    if (score == 0) {
        score = DISCOVERY_SCORE
        reasons += "library discovery"
    }

    return SuggestedRecommendation(
        track = track,
        reason = reasons.firstOrNull() ?: "good match for your queue",
        score = score,
    )
}

/** Long enough to be a song rather than an interlude, short enough not to be a mix. */
private val SONG_LENGTH_MS = 150_000L..360_000L

private const val DEFAULT_LIMIT = 60

/** Wider than a phone screen holds, so one press turns the whole list over. */
private const val ROTATION_STRIDE = 5
private const val SAME_ARTIST = 120
private const val ARTIST_IN_QUEUE = 70
private const val FAVOURITE = 35
private const val LIKED_SUGGESTION = 90
private const val ARTIST_PLAYS_CAP = 20
private const val ARTIST_PLAY_WEIGHT = 4
private const val TRACK_PLAYS_CAP = 10
private const val TRACK_PLAY_WEIGHT = 2
private const val TRACK_SKIPS_CAP = 10
private const val TRACK_SKIP_WEIGHT = 9
private const val SEED_WORD_MINIMUM = 4
private const val TITLE_SIMILARITY = 20
private const val SONG_LENGTH = 8
private const val DISCOVERY_SCORE = 1
