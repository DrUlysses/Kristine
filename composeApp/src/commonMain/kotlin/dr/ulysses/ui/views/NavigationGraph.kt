package dr.ulysses.ui.views

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.models.MainViewModel
import dr.ulysses.ui.components.*
import dr.ulysses.ui.elements.LoadingIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NavGraphBuilder.AddNavigationGraph(
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
                onClick = { song ->
                    onPlaylistChanged(Playlist(songs = currentArtistSongsList))
                    onPlaySongCommand(song)
                },
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
                onClick = { song ->
                    onPlaylistChanged(Playlist(songs = currentAlbumSongsList))
                    onPlaySongCommand(song)
                },
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
                onClick = { song ->
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

    navigation<ManageUnsortedGraph>(
        startDestination = ManageUnsortedList,
        popExitTransition = {
            setTopBarText(null)
            fadeOut(animationSpec = tween(1))
        }
    ) {
        composable<ManageUnsortedList> {
            var unsortedSongs by remember { mutableStateOf(emptyList<Song>()) }

            CoroutineScope(Dispatchers.Default).launch {
                unsortedSongs = SongRepository.getByNotState(Song.State.Sorted)
            }

            if (unsortedSongs.isNotEmpty()) {
                ManageUnsortedList(
                    unsortedSongs = unsortedSongs,
                    onClick = { song ->
                        MainViewModel.setSelectedSong(song)
                        navBarController.navigate(ManageSong)
                    }
                )
            } else {
                LoadingIndicator()
            }
        }

        composable<ManageSong> {
            var unsortedSongs by rememberSaveable { mutableStateOf(emptyList<Song>()) }
            CoroutineScope(Dispatchers.Default).launch {
                unsortedSongs = SongRepository.getByNotState(Song.State.Sorted)
            }
            val currentSong = MainViewModel.state.selectedSong
            val currentSongIndex = unsortedSongs.indexOf(currentSong)
            if (currentSong != null && unsortedSongs.isNotEmpty()) {
                ManageUnsortedSong(
                    song = currentSong,
                    unsortedSongs = unsortedSongs,
                    onSongEdited = { edited ->
                        // Save the edited song to the database
                        CoroutineScope(Dispatchers.Default).launch {
                            SongRepository.upsert(edited)
                            onSongSave(edited)
                        }
                        MainViewModel.setSelectedSong(edited)
                    },
                    onNextSong = {
                        MainViewModel.setSelectedSong(unsortedSongs.getOrNull(currentSongIndex + 1))
                    },
                    onPreviousSong = {
                        MainViewModel.setSelectedSong(unsortedSongs.getOrNull(currentSongIndex - 1))
                    }
                )
            } else {
                LoadingIndicator()
            }
        }
    }

    composable<Connections> {
        Connections()
    }

    composable<Settings> {
        Settings(
            onPendingSaveJobsChanged = MainViewModel::setPendingSaveJobs,
            onSongsPathChanged = MainViewModel::setSongsPathChanged
        )
    }
}
