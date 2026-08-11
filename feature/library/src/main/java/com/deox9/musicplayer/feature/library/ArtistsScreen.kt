// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.deox9.musicplayer.library.ArtistInfo
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.ui.CollectionSortOption
import com.deox9.musicplayer.ui.buildAlphabetIndex
import com.deox9.musicplayer.ui.shouldShowFastScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artists in the library.
 *
 * The tab the app was missing: "who is this by" is one of the three questions a music
 * library gets asked, and until now the only answer was typing the name into a filter
 * and hoping the spelling matched.
 */
@Composable
fun ArtistsScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    searchQuery: String = "",
    sortOption: CollectionSortOption = CollectionSortOption.Name,
    listState: LazyListState,
) {
    var selected by remember { mutableStateOf<ArtistInfo?>(null) }
    val artists by viewModel.artists.collectAsState()

    ListDetailPane(
        detail = selected?.let { artist ->
            {
                ArtistDetailScreen(artist = artist, onBack = { selected = null })
            }
        },
    ) {
        ArtistsList(
            artists = artists,
            searchQuery = searchQuery,
            sortOption = sortOption,
            listState = listState,
            onSelect = { selected = it },
        )
    }
}

@Composable
private fun ArtistsList(
    artists: List<ArtistInfo>,
    searchQuery: String,
    sortOption: CollectionSortOption,
    listState: LazyListState,
    onSelect: (ArtistInfo) -> Unit,
) {
    val filtered = remember(artists, searchQuery, sortOption) {
        val searched = if (searchQuery.isBlank()) {
            artists
        } else {
            val q = searchQuery.trim().lowercase()
            artists.filter { it.name.lowercase().contains(q) }
        }

        when (sortOption) {
            CollectionSortOption.Name -> searched
            CollectionSortOption.TrackCount -> searched.sortedByDescending { it.trackCount }
        }
    }

    if (filtered.isEmpty()) {
        Box(modifier = Modifier.padding(16.dp)) {
            Text(if (searchQuery.isBlank()) "No artists found." else "No artists match your search.")
        }
        return
    }

    val buckets = remember(filtered) { buildAlphabetIndex(filtered.map { it.name }) }
    val showRail = shouldShowFastScroll(filtered.size, sortOption == CollectionSortOption.Name)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = if (showRail) FastScrollGutter else 0.dp),
        ) {
            items(filtered, key = { it.id }) { artist ->
                ArtistRow(artist = artist, onClick = { onSelect(artist) })
                HorizontalDivider()
            }
        }

        if (showRail) {
            FastScrollRail(
                buckets = buckets,
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ArtistRow(artist: ArtistInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, onClickLabel = "Show tracks")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Artists have no artwork of their own in the index — MediaStore does not
        // carry artist images and fetching them would mean a network call, which
        // this build does not make. A circular monogram reads as "person" without
        // pretending to be a photo.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = artistSubtitle(artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Plural-aware so a one-track artist does not read "1 tracks". */
private fun artistSubtitle(artist: ArtistInfo): String {
    val tracks = "${artist.trackCount} ${if (artist.trackCount == 1) "track" else "tracks"}"
    if (artist.albumCount <= 1) return tracks
    return "${artist.albumCount} albums · $tracks"
}

@Composable
private fun ArtistDetailScreen(
    artist: ArtistInfo,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val tracks by produceState<List<LocalTrack>>(initialValue = emptyList(), key1 = artist.id) {
        value = withContext(Dispatchers.IO) { viewModel.tracksByArtist(artist.id) }
    }

    CollectionTrackListScreen(
        title = artist.name,
        subtitle = artistSubtitle(artist),
        tracks = tracks,
        onBack = onBack,
    )
}
