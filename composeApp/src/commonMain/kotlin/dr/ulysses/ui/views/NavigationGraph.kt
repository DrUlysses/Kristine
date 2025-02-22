package dr.ulysses.ui.views

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun NavGraphBuilder.addNavigationGraph(
    navBarController: NavController,
    setTopBarText: (String?) -> Unit,
    pagerState: PagerState,
    onPlaySongCommand: (Song) -> Unit,
    onPlaylistChanged: (Playlist) -> Unit,
    currentPlaylist: Playlist,
    onCurrentPlaylistChanged: (Playlist) -> Unit,
    searchText: String,
    currentArtistSongsList: List<Song>,
    currentAlbumSongsList: List<Song>,
) {
    val scope = rememberCoroutineScope()

    navigation<ArtistsGraph>(
        startDestination = Artists,
        popExitTransition = {
            setTopBarText(null)
            fadeOut(animationSpec = tween(1))
        }
    ) {
        composable<Artists> {
            scope.launch {
                pagerState.scrollToPage(0)
            }
        }
        composable<ArtistSongs> {
            SongsList(
                songs = currentArtistSongsList,
                onPlaySongCommand = onPlaySongCommand,
            )
        }
    }

    composable<SongsList> {
        scope.launch {
            pagerState.scrollToPage(1)
        }
    }

    navigation<AlbumsGraph>(
        startDestination = Albums,
        popExitTransition = {
            setTopBarText(null)
            fadeOut(animationSpec = tween(1))
        }
    ) {
        composable<Albums> {
            scope.launch {
                pagerState.scrollToPage(2)
            }
        }
        composable<AlbumSongs> {
            SongsList(
                songs = currentAlbumSongsList,
                onPlaySongCommand = onPlaySongCommand,
            )
        }
    }

    navigation<PlaylistsGraph>(
        startDestination = Playlists,
        popExitTransition = {
            setTopBarText(null)
            fadeOut(animationSpec = tween(1))
        }
    ) {
        composable<Playlists> {
            scope.launch {
                pagerState.scrollToPage(3)
            }
        }
        composable<PlaylistSongs> {
            var songs by remember { mutableStateOf(emptyList<Song>()) }

            scope.launch {
                songs = PlaylistRepository.getPlaylistSongs(currentPlaylist.name)
            }

            SongsList(
                songs = songs,
                onPlaySongCommand = { song ->
                    onPlaylistChanged(currentPlaylist.copy(songs = songs))
                    onPlaySongCommand(song)
                },
            )
        }
    }

    navigation<SearchGraph>(
        startDestination = SearchEntries,
        popExitTransition = {
            setTopBarText(null)
            fadeOut(animationSpec = tween(1))
        }
    ) {
        composable<SearchEntries> {
            Search(
                onPlaylistClicked = { playlist ->
                    onCurrentPlaylistChanged(playlist as Playlist)
                    navBarController.navigate(PlaylistSongs)
                },
                onQueryChanged = { query ->
                    setTopBarText(query.ifEmpty { searchText })
                }
            )
        }
    }

    composable<ManagePlaylist> {
        ManagePlaylist(
            playlist = currentPlaylist,
            onPlaylistChanged = onCurrentPlaylistChanged
        )
    }
}
