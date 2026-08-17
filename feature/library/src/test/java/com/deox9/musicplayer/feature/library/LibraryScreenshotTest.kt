// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.deox9.musicplayer.library.Album
import com.deox9.musicplayer.library.ArtistInfo
import com.deox9.musicplayer.library.ArtistTally
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.ListeningStats
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.TrackTally
import com.deox9.musicplayer.ui.AlbumSortOption
import com.deox9.musicplayer.ui.CollectionSortOption
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images for the library's lists and the listening statistics screen.
 *
 * Two different reasons these are only possible now. The stats screen reached for a
 * Hilt view model and had to be split into a wrapper and a content composable before
 * it could be drawn at all. The five lists were already written the right way —
 * plain values in, a callback out — and were merely `private`, so nothing outside
 * the file could see them. Both are the same underlying point: a composable that can
 * be handed its data is a composable that can be looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LibraryScreenshotTest {

    private fun track(id: Long, title: String, artist: String) = LocalTrack(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = 214_000L,
        contentUri = "content://media/external/audio/media/$id",
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxWidth()) { content() }
            }
        }
    }

    @Test
    fun `listening stats with a history`() {
        capture("listening-stats") {
            ListeningStatsContent(
                stats = ListeningStats(
                    totalPlays = 343,
                    distinctTracksPlayed = 59,
                    totalSkips = 42,
                    topArtists = listOf(
                        ArtistTally("Sunmisola Agbebi", 41),
                        ArtistTally("Atenteben Edem", 11),
                    ),
                    topTracks = listOf(
                        TrackTally(track(1, "Peace Be Still", "Sunmisola Agbebi"), 12),
                    ),
                    mostSkipped = listOf(TrackTally(track(2, "Interlude", "Various"), 6)),
                    playsOfMissingTracks = 7,
                ),
                onBack = {},
            )
        }
    }

    /**
     * Nothing played yet — the state a new install is in, and the one most easily
     * broken by working only against a library that already has history.
     */
    @Test
    fun `listening stats with nothing to show`() {
        capture("listening-stats-empty") {
            ListeningStatsContent(
                stats = ListeningStats(
                    totalPlays = 0,
                    distinctTracksPlayed = 0,
                    totalSkips = 0,
                    topArtists = emptyList(),
                    topTracks = emptyList(),
                    mostSkipped = emptyList(),
                    playsOfMissingTracks = 0,
                ),
                onBack = {},
            )
        }
    }

    @Test
    fun `genres list`() {
        capture("genres-list") {
            GenresList(
                genres = listOf(
                    GenreInfo(1, "Afrobeats", 42),
                    GenreInfo(2, "Gospel", 18),
                    GenreInfo(3, "Highlife", 7),
                ),
                searchQuery = "",
                sortOption = CollectionSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
            )
        }
    }

    /**
     * A search that matches nothing.
     *
     * The empty result is a different layout from the empty library, and it is the
     * one a person sees most often — every time they mistype.
     */
    @Test
    fun `genres list with no matches`() {
        capture("genres-list-no-matches") {
            GenresList(
                genres = listOf(GenreInfo(1, "Afrobeats", 42)),
                searchQuery = "nothing matches this",
                sortOption = CollectionSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
            )
        }
    }

    @Test
    fun `folders list`() {
        capture("folders-list") {
            FoldersList(
                folders = listOf(
                    FolderInfo("/storage/emulated/0/Music", "Music", 96),
                    FolderInfo("/storage/emulated/0/Download", "Download", 31),
                ),
                searchQuery = "",
                sortOption = CollectionSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
                onHide = {},
            )
        }
    }

    @Test
    fun `playlists list`() {
        capture("playlists-list") {
            PlaylistsList(
                playlists = listOf(
                    PlaylistInfo(1, "Morning", 14),
                    PlaylistInfo(2, "Long drive", 63),
                ),
                searchQuery = "",
                sortOption = CollectionSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
                onExport = {},
                onDelete = {},
            )
        }
    }

    @Test
    fun `artists list`() {
        capture("artists-list") {
            ArtistsList(
                artists = listOf(
                    ArtistInfo(1, "Sunmisola Agbebi", 12, 2),
                    ArtistInfo(2, "Burna Boy", 31, 4),
                ),
                searchQuery = "",
                sortOption = CollectionSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
            )
        }
    }

    @Test
    fun `albums list`() {
        capture("albums-list") {
            AlbumsList(
                albums = listOf(
                    Album(1, "Live at the Sanctuary", "Sunmisola Agbebi", null, 12),
                    Album(2, "Twice as Tall", "Burna Boy", null, 15),
                ),
                searchQuery = "",
                sortOption = AlbumSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
            )
        }
    }
}
