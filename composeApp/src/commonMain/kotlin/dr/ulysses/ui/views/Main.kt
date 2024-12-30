package dr.ulysses.ui.views

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.models.PlayerService
import dr.ulysses.ui.components.*
import dr.ulysses.ui.permissions.PermissionsAlert
import kotlinx.coroutines.launch
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.search
import kristine.composeapp.generated.resources.search_tooltip
import org.jetbrains.compose.resources.stringResource

enum class Navigation {
    Artists,
    Songs,
    Albums,
    Playlists
}

@Composable
fun Main() {
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { Navigation.entries.size },
    )
    val searchText = stringResource(Res.string.search)
    val searchTooltip = stringResource(Res.string.search_tooltip)
    val permissionsGranted = remember { mutableStateOf(false) }
    val playerModel = remember { PlayerService }
    val scope = rememberCoroutineScope()
    val playerState = playerModel.state
    var topBarText by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf(false) }
    val navBarController = rememberNavController()
    val currentSongs by remember {
        mutableStateOf(
            playerState
                .currentTrackSequence
                .values
                .toList()
        )
    }
    var currentArtistSongsList by remember { mutableStateOf(emptyList<Song>()) }
    var currentAlbumSongsList by remember { mutableStateOf(emptyList<Song>()) }
    var currentPlaylists by remember { mutableStateOf(emptyList<Playlist>()) }
    PermissionsAlert(
        permissionsGranted = permissionsGranted.value,
        onPermissionsChange = {
            permissionsGranted.value = it
        }
    )
    Scaffold(
        topBar = {
            TabMenu(
                pagerState = pagerState,
                topText = topBarText,
                navigateUp = {
                    navBarController.navigateUp()
                    topBarText = null
                }
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (search) {
                    false -> {
                        HorizontalPager(
                            modifier = Modifier.fillMaxSize(),
                            state = pagerState,
                            pageContent = { page ->
                                when (page) {
                                    Navigation.Artists.ordinal -> {
                                        NavHost(
                                            navController = navBarController,
                                            startDestination = Navigation.Artists.name,
                                            popExitTransition = {
                                                topBarText = null
                                                fadeOut(animationSpec = tween(200))
                                            },
                                            route = "${Navigation.Artists.name} List"
                                        ) {
                                            composable(Navigation.Artists.name) {
                                                ArtistsList(
                                                    artists = currentSongs
                                                        .map { it.artist }
                                                        .fastDistinctBy(String::lowercase),
                                                    onArtistsChanged = {},
                                                    onArtistClicked = { artist ->
                                                        topBarText = artist
                                                        currentArtistSongsList =
                                                            playerState.currentTrackSequence.values.filter {
                                                                topBarText != null &&
                                                                        it.artist.lowercase() == topBarText!!.lowercase()
                                                            }
                                                        navBarController.navigate(Navigation.Artists.name.dropLast(1))
                                                    }
                                                )
                                            }
                                            composable(Navigation.Artists.name.dropLast(1)) {
                                                SongsList(
                                                    songs = currentArtistSongsList,
                                                    onSongsChanged = playerModel::onSongsChanged,
                                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                                )
                                            }
                                        }
                                    }

                                    Navigation.Songs.ordinal -> {
                                        SongsList(
                                            songs = currentSongs,
                                            onSongsChanged = playerModel::onSongsChanged,
                                            onPlaySongCommand = playerModel::onPlaySongCommand,
                                        )
                                    }

                                    Navigation.Albums.ordinal -> {
                                        NavHost(
                                            navController = navBarController,
                                            startDestination = Navigation.Albums.name,
                                            popExitTransition = {
                                                topBarText = null
                                                fadeOut(animationSpec = tween(200))
                                            },
                                            route = "${Navigation.Albums.name} List"
                                        ) {
                                            composable(Navigation.Albums.name) {
                                                AlbumsList(
                                                    albums = playerState.currentTrackSequence.values.mapNotNull { it.album }
                                                        .fastDistinctBy(String::lowercase),
                                                    onAlbumsChanged = {},
                                                    onAlbumClicked = { album ->
                                                        topBarText = album
                                                        currentAlbumSongsList =
                                                            playerState.currentTrackSequence.values.filter {
                                                                it.album != null && topBarText != null &&
                                                                        it.album.lowercase() == topBarText!!.lowercase()
                                                            }
                                                        navBarController.navigate(Navigation.Albums.name.dropLast(1))
                                                    }
                                                )
                                            }
                                            composable(Navigation.Albums.name.dropLast(1)) {
                                                SongsList(
                                                    songs = currentAlbumSongsList,
                                                    onSongsChanged = playerModel::onSongsChanged,
                                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                                )
                                            }
                                        }
                                    }

                                    Navigation.Playlists.ordinal -> {
                                        PlaylistsList(
                                            playlists = currentPlaylists.ifEmpty {
                                                scope.launch {
                                                    currentPlaylists = PlaylistRepository.getAllPlaylists()
                                                }
                                                currentPlaylists
                                            },
                                            onPlaylistsChanged = {
                                                scope.launch {
                                                    currentPlaylists = PlaylistRepository.getAllPlaylists()
                                                }
                                            },
                                            onPlaylistClicked = { playlist ->
                                                currentArtistSongsList = playlist.songs
                                                navBarController.navigate(Navigation.Playlists.name.dropLast(1))
                                            }
                                        )
                                    }

                                    else -> listOf<Song>()
                                }
                            }
                        )
                    }

                    true -> {

                    }
                }
            }
        },
        bottomBar = {
            Player(
                currentSong = playerState.currentSong,
                isPlaying = playerState.isPlaying,
                onPreviousCommand = playerModel::onPreviousCommand,
                onNextCommand = playerModel::onNextCommand,
                onPlayOrPauseCommand = playerModel::onPlayOrPauseCommand,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    search = !search
                    topBarText = if (search) searchText else null
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = searchTooltip
                )
            }
        }
    )
}
