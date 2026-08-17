// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.library.ListeningStats
import com.deox9.musicplayer.library.TrackTally
import com.deox9.musicplayer.library.listeningStats

/**
 * What the app has noticed about someone's listening, shown to them.
 *
 * These counts have been collected since the first version and displayed nowhere —
 * they existed only to rank the Suggested tab. Nothing new is recorded to build this
 * screen. That is the point of it: the app counts what you play, so it should be
 * willing to tell you what it counted.
 *
 * Read-only by design. A "clear my history" control belongs here eventually, but a
 * screen that both reveals and destroys on the first release is a screen where the
 * destructive half gets pressed by someone still working out what the numbers mean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningStatsScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks by viewModel.tracks.collectAsState()
    val signals by viewModel.recommendationSignals.collectAsState()
    val stats = remember(tracks, signals) { listeningStats(tracks, signals) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your listening") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { innerPadding ->
        if (!stats.hasAnything) {
            EmptyStats(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatsSummary(stats) }

            if (stats.topArtists.isNotEmpty()) {
                item { SectionHeading("Most played artists") }
                items(stats.topArtists, key = { "artist-${it.artist}" }) { tally ->
                    StatRow(name = tally.artist, count = tally.plays, unit = "play")
                }
            }

            if (stats.topTracks.isNotEmpty()) {
                item { SectionHeading("Most played tracks") }
                // Keys are namespaced per section. A track that is both played often
                // and skipped often appears in two lists, and a LazyColumn throws on
                // a duplicate key — which crashed the screen the moment real data put
                // the same track in both.
                items(stats.topTracks, key = { "played-${it.track.id}" }) { tally ->
                    TrackStatRow(tally, unit = "play")
                }
            }

            if (stats.mostSkipped.isNotEmpty()) {
                item { SectionHeading("Most skipped") }
                items(stats.mostSkipped, key = { "skipped-${it.track.id}" }) { tally ->
                    TrackStatRow(tally, unit = "skip")
                }
            }

            item { PrivacyNote() }
        }
    }
}

@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp)) {
        Text("Nothing to show yet", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Play some music and this fills in. Counts are kept on this device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsSummary(stats: ListeningStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${stats.totalPlays} ${plural(stats.totalPlays, "play")}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "across ${stats.distinctTracksPlayed} " +
                    "${plural(stats.distinctTracksPlayed, "track")} · " +
                    "${stats.skipPercentage}% skipped",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Named rather than folded silently into the total, so the numbers add up
            // for someone who has deleted music since playing it.
            if (stats.playsOfMissingTracks > 0) {
                Text(
                    text = "${stats.playsOfMissingTracks} of those were tracks no longer " +
                        "on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun TrackStatRow(tally: TrackTally, unit: String) {
    StatRow(
        name = tally.track.title,
        secondary = tally.track.artist.takeIf { it.isNotBlank() },
        count = tally.count,
        unit = unit,
    )
}

@Composable
private fun StatRow(
    name: String,
    count: Int,
    unit: String,
    secondary: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "$count ${plural(count, unit)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        text = "These counts are stored on this device and are never sent anywhere. " +
            "Only totals are kept, so this cannot say what you played on any given day.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

private fun plural(count: Int, singular: String) = if (count == 1) singular else "${singular}s"
