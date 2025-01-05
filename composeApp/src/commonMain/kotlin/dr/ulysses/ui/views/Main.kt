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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import dr.ulysses.entities.SongRepository
import dr.ulysses.models.PlayerService
import dr.ulysses.ui.components.*
import dr.ulysses.ui.permissions.PermissionsAlert
import dr.ulysses.ui.views.Navigation.entries
import kotlinx.coroutines.launch
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.add_playlist
import kristine.composeapp.generated.resources.search
import kristine.composeapp.generated.resources.search_tooltip
import org.jetbrains.compose.resources.stringResource

enum class Navigation {
    Artists,
    Songs,
    Albums,
    Playlists,
    Search;

    companion object {
        val shownEntries = entries.filter { it != Search }
    }
}

enum class Views {
    Main,
    Search,
    ManagePlaylist
}

@Composable
fun Main() {
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { Navigation.shownEntries.size },
    )
    val searchText = stringResource(Res.string.search)
    val addPlaylistText = stringResource(Res.string.add_playlist)
    val searchTooltip = stringResource(Res.string.search_tooltip)
    val permissionsGranted = remember { mutableStateOf(false) }
    val playerModel = remember { PlayerService }
    val scope = rememberCoroutineScope()
    val playerState = playerModel.state
    var topBarText by remember { mutableStateOf<String?>(null) }
    var view by remember { mutableStateOf(Views.Main) }
    val navBarController = rememberNavController()
    var route by remember { mutableStateOf<String?>(null) }
    var allSongs by remember { mutableStateOf(emptyList<Song>()) }
    allSongs = run {
        scope.launch {
            allSongs = SongRepository.getAllSongs()
        }
        allSongs
    }
    var currentArtistSongsList by remember { mutableStateOf(emptyList<Song>()) }
    var currentAlbumSongsList by remember { mutableStateOf(emptyList<Song>()) }
    var currentPlaylist by remember {
        mutableStateOf<Playlist>(
            Playlist(
                songs = allSongs,
            )
        )
    }
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
                    when (view) {
                        Views.Search, Views.ManagePlaylist -> view = Views.Main
                        else -> navBarController.navigateUp()
                    }
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
                when (view) {
                    Views.Main ->
                        HorizontalPager(
                            modifier = Modifier.fillMaxSize(),
                            state = pagerState,
                            pageContent = { page ->
                                when {
                                    page == Navigation.Artists.ordinal -> {
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
                                                    artists = allSongs
                                                        .map { it.artist.trim() }
                                                        .fastDistinctBy(String::lowercase),
                                                    onArtistsChanged = {},
                                                    onArtistClicked = { artist ->
                                                        topBarText = artist
                                                        currentArtistSongsList = allSongs.filter {
                                                            it.artist.trim().lowercase() == artist.trim().lowercase()
                                                        }
                                                        navBarController.navigate(Navigation.Artists.name.dropLast(1))
                                                    }
                                                )
                                            }
                                            composable(Navigation.Artists.name.dropLast(1)) {
                                                SongsList(
                                                    songs = currentArtistSongsList,
                                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                                )
                                            }
                                        }
                                    }

                                    page == Navigation.Songs.ordinal -> {
                                        SongsList(
                                            songs = allSongs,
                                            onPlaySongCommand = { song ->
                                                playerModel.onSongsChanged(allSongs)
                                                playerModel.onPlaySongCommand(song)
                                            },
                                        )
                                    }

                                    page == Navigation.Albums.ordinal -> {
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
                                                    albums = allSongs
                                                        .mapNotNull { it.album?.trim() }
                                                        .fastDistinctBy(String::lowercase),
                                                    onAlbumClicked = { album ->
                                                        topBarText = album
                                                        currentAlbumSongsList = allSongs.filter {
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
                                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                                )
                                            }
                                        }
                                    }

                                    pagerState.currentPage == Navigation.Playlists.ordinal -> {
                                        NavHost(
                                            navController = navBarController,
                                            startDestination = Navigation.Playlists.name,
                                            popExitTransition = {
                                                topBarText = null
                                                fadeOut(animationSpec = tween(200))
                                            },
                                            route = "${Navigation.Playlists.name} List"
                                        ) {
                                            composable(Navigation.Playlists.name) {
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
                                                        currentPlaylist = playlist
                                                        topBarText = currentPlaylist.name
                                                        navBarController.navigate(Navigation.Playlists.name.dropLast(1))
                                                    }
                                                )
                                            }
                                            composable(Navigation.Playlists.name.dropLast(1)) {
                                                var songs by remember { mutableStateOf(emptyList<Song>()) }

                                                scope.launch {
                                                    songs = PlaylistRepository.getPlaylistSongs(currentPlaylist.name)
                                                }

                                                SongsList(
                                                    songs = songs,
                                                    onPlaySongCommand = { song ->
                                                        playerModel.onPlaylistChanged(currentPlaylist.copy(songs = songs))
                                                        playerModel.onPlaySongCommand(song)
                                                    },
                                                )
                                            }
                                        }
                                    }

                                    else -> listOf<Song>()
                                }
                                route = if (navBarController.visibleEntries.value.isEmpty())
                                    null
                                else
                                    navBarController.graph.route
                            }
                        )

                    Views.Search ->
                        NavHost(
                            navController = navBarController,
                            startDestination = Navigation.Search.name,
                            popExitTransition = {
                                topBarText = null
                                fadeOut(animationSpec = tween(200))
                            },
                            route = "${Navigation.Search.name} List"
                        ) {
                            composable(Navigation.Search.name) {
                                Search(
                                    onPlaylistClicked = { playlist ->
                                        currentPlaylist = playlist as Playlist
                                        navBarController.navigate(Navigation.Playlists.name.dropLast(1))
                                    },
                                    onQueryChanged = { query ->
                                        topBarText = query.ifEmpty { searchText }
                                    }
                                )
                            }
                        }

                    Views.ManagePlaylist ->
                        ManagePlaylist(
                            playlist = currentPlaylist,
                            onPlaylistChanged = { playlist ->
                                currentPlaylist = playlist
                            }
                        )
                }
            }
        },
        bottomBar = {
            Player(
                currentSong = playerState.currentSong,
                isPlaying = playerState.isPlaying,
                isShuffling = playerState.shuffle,
                repeatMode = playerState.repeatMode,
                onPreviousCommand = playerModel::onPreviousCommand,
                onNextCommand = playerModel::onNextCommand,
                onPlayOrPauseCommand = playerModel::onPlayOrPauseCommand,
                onToggleShuffleCommand = playerModel::onToggleShuffleCommand,
                onSwitchRepeatCommand = playerModel::onSwitchRepeatCommand,
            )
        },
        floatingActionButton = {
            when (view) {
                Views.Search -> FloatingActionButton(
                    onClick = {
                        view = Views.Main
                        topBarText = null
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = searchTooltip
                    )
                }

                Views.ManagePlaylist -> FloatingActionButton(
                    onClick = {
                        scope.launch {
                            PlaylistRepository.insert(currentPlaylist)
                            allSongs = SongRepository.getAllSongs()
                            currentPlaylist = Playlist(
                                songs = allSongs,
                            )
                            view = Views.Main
                            topBarText = null
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = searchTooltip
                    )
                }

                else -> when (route) {
                    "${Navigation.Playlists.name} List" -> FloatingActionButton(
                        onClick = {
                            topBarText = addPlaylistText
                            view = Views.ManagePlaylist
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = searchTooltip
                        )
                    }

                    else -> FloatingActionButton(
                        onClick = {
                            view = Views.Search
                            topBarText = searchText
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = searchTooltip
                        )
                    }
                }
            }
        }
    )
}
