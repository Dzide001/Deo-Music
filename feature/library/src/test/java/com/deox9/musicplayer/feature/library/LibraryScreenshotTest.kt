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
import com.deox9.musicplayer.library.AutoPlaylist
import com.deox9.musicplayer.library.AutoPlaylistKind
import com.deox9.musicplayer.library.FolderInfo
import com.deox9.musicplayer.library.GenreInfo
import com.deox9.musicplayer.library.ListeningStats
import com.deox9.musicplayer.library.LocalTrack
import com.deox9.musicplayer.library.PlaylistInfo
import com.deox9.musicplayer.library.TrackTally
import com.deox9.musicplayer.ui.AlbumSortOption
import com.deox9.musicplayer.ui.CollectionSortOption
import com.deox9.musicplayer.ui.SongSortOption
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference images for the library's lists and the listening statistics screen.
 *
 * Two different reasons these are possible now. Five screens — the statistics,
 * suggestions, songs, favourites and search — reached for a Hilt view model and had
 * to be split into a wrapper that collects state and a content composable that takes
 * values, before they could be drawn at all. The five collection lists were already
 * written the right way, plain values in and a callback out, and were merely
 * `private` so nothing outside the file could see them. Both are the same underlying
 * point: a composable that can be handed its data is a composable that can be looked
 * at.
 *
 * The empty states are here deliberately, and there are three distinct ones that are
 * easy to conflate. An empty library says the collection is empty; an empty search
 * says the filter is; an empty favourites list is an invitation rather than either.
 * All three are invisible while developing against data that matches.
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
                autoPlaylists = emptyList(),
                searchQuery = "",
                sortOption = CollectionSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
                onSelectAuto = {},
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

    private val songs = listOf(
        track(1, "Peace Be Still", "Sunmisola Agbebi"),
        track(2, "Anybody", "Burna Boy"),
        track(3, "Terminator", "Asake"),
    )

    @Test
    fun `songs list`() {
        capture("songs-list") {
            SongsList(
                tracks = songs,
                favourites = setOf(songs[1].contentUri),
                searchQuery = "",
                sortOption = SongSortOption.Title,
                listState = rememberLazyListState(),
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    /** A library with nothing in it — what a fresh install shows before a scan. */
    @Test
    fun `songs list with an empty library`() {
        capture("songs-list-empty") {
            SongsList(
                tracks = emptyList(),
                favourites = emptySet(),
                searchQuery = "",
                sortOption = SongSortOption.Title,
                listState = rememberLazyListState(),
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    /**
     * A search matching nothing, which says something different from an empty
     * library: the filter is the problem, not the collection.
     */
    @Test
    fun `songs list with no matches`() {
        capture("songs-list-no-matches") {
            SongsList(
                tracks = songs,
                favourites = emptySet(),
                searchQuery = "zzzz",
                sortOption = SongSortOption.Title,
                listState = rememberLazyListState(),
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    @Test
    fun `favourites list`() {
        capture("favourites-list") {
            FavouritesList(
                tracks = songs,
                favourites = setOf(songs[0].contentUri, songs[2].contentUri),
                searchQuery = "",
                sortOption = SongSortOption.Title,
                listState = rememberLazyListState(),
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    /**
     * Nothing starred yet. An invitation, not a failure — and worded as one.
     *
     * Both this and the populated case were also driven on a phone: double-tapping a
     * row stars it and it appears here, double-tapping again removes it and the
     * screen returns to this state.
     */
    @Test
    fun `favourites list with nothing starred`() {
        capture("favourites-list-empty") {
            FavouritesList(
                tracks = songs,
                favourites = emptySet(),
                searchQuery = "",
                sortOption = SongSortOption.Title,
                listState = rememberLazyListState(),
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    @Test
    fun `search with results`() {
        capture("search-results") {
            SearchResults(
                query = "peace",
                onQueryChange = {},
                trackResults = listOf(songs[0]),
                albumResults = listOf(Album(1, "Live at the Sanctuary", "Sunmisola Agbebi", null, 12)),
                favourites = emptySet(),
                listState = rememberLazyListState(),
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    /** The state a person hits every time they mistype. */
    @Test
    fun `search with no results`() {
        capture("search-no-results") {
            SearchResults(
                query = "zzzz",
                onQueryChange = {},
                trackResults = emptyList(),
                albumResults = emptyList(),
                favourites = emptySet(),
                listState = rememberLazyListState(),
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }

    /**
     * Generated lists sit above the ones someone made, and say where they came from.
     *
     * Pinned because "made for you" is the only thing distinguishing a playlist
     * nobody created from the ones they did, and it is a line of text easy to lose.
     */
    @Test
    fun `playlists with generated ones above them`() {
        capture("playlists-with-generated") {
            PlaylistsList(
                playlists = listOf(PlaylistInfo(1, "Morning", 14)),
                autoPlaylists = listOf(
                    AutoPlaylist(AutoPlaylistKind.MostPlayed, songs),
                    AutoPlaylist(AutoPlaylistKind.RecentlyAdded, songs.take(1)),
                ),
                searchQuery = "",
                sortOption = CollectionSortOption.Name,
                listState = rememberLazyListState(),
                onSelect = {},
                onSelectAuto = {},
                onExport = {},
                onDelete = {},
            )
        }
    }

    @Test
    fun `a generated playlist's tracks`() {
        capture("auto-playlist-detail") {
            AutoPlaylistDetail(
                playlist = AutoPlaylist(AutoPlaylistKind.MostPlayed, songs),
                favourites = setOf(songs[0].contentUri),
                onBack = {},
                onToggleFavourite = {},
                onPlay = { _, _ -> },
                onPlayNext = {},
                onAddToQueue = {},
            )
        }
    }
}
