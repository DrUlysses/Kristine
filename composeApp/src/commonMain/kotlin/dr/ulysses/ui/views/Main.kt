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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.models.PlayerService
import dr.ulysses.ui.components.*
import dr.ulysses.ui.permissions.PermissionsAlert
import kotlinx.coroutines.launch
import kristine.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun Main() {
    val pagerState = rememberPagerState(
        initialPage = 1,
        pageCount = { 4 },
    )
    val searchText = stringResource(Res.string.search)
    val addPlaylistText = stringResource(Res.string.add_playlist)
    val searchTooltip = stringResource(Res.string.search_tooltip)
    val permissionsGranted = remember { mutableStateOf(false) }
    val playerModel = remember { PlayerService }
    val scope = rememberCoroutineScope()
    val playerState = playerModel.state
    var topBarText by remember { mutableStateOf<String?>(null) }
    val navBarController = rememberNavController()
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
                    navBarController.navigateUp()
                    topBarText = null
                },
                tabs = mapOf(
                    0 to stringResource(Res.string.artists),
                    1 to stringResource(Res.string.songs),
                    2 to stringResource(Res.string.albums),
                    3 to stringResource(Res.string.playlists),
                )
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState,
                ) { page ->
                    try {
                        if (topBarText == null)
                            navBarController.navigate(
                                when (page) {
                                    0 -> Artists
                                    1 -> SongsList
                                    2 -> Albums
                                    3 -> Playlists
                                    else -> SongsList
                                }
                            )
                    } catch (_: IllegalStateException) {
                    }

                    NavHost(
                        navController = navBarController,
                        startDestination = SongsList,
                        popExitTransition = {
                            topBarText = null
                            fadeOut(animationSpec = tween(100))
                        }
                    ) {
                        navigation<ArtistsGraph>(
                            startDestination = Artists,
                            popExitTransition = {
                                topBarText = null
                                fadeOut(animationSpec = tween(100))
                            }
                        ) {
                            composable<Artists> {
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
                                        navBarController.navigate(ArtistSongs)
                                    }
                                )
                            }
                            composable<ArtistSongs> {
                                SongsList(
                                    songs = currentArtistSongsList,
                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                )
                            }
                        }

                        composable<SongsList> {
                            SongsList(
                                songs = allSongs,
                                onPlaySongCommand = { song ->
                                    playerModel.onSongsChanged(allSongs)
                                    playerModel.onPlaySongCommand(song)
                                },
                            )
                        }

                        navigation<AlbumsGraph>(
                            startDestination = Albums,
                            popExitTransition = {
                                topBarText = null
                                fadeOut(animationSpec = tween(100))
                            }
                        ) {
                            composable<Albums> {
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
                                        navBarController.navigate(AlbumSongs)
                                    }
                                )
                            }
                            composable<AlbumSongs> {
                                SongsList(
                                    songs = currentAlbumSongsList,
                                    onPlaySongCommand = playerModel::onPlaySongCommand,
                                )
                            }
                        }

                        navigation<PlaylistsGraph>(
                            startDestination = Playlists,
                            popExitTransition = {
                                topBarText = null
                                fadeOut(animationSpec = tween(100))
                            }
                        ) {
                            composable<Playlists> {
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
                                        navBarController.navigate(PlaylistSongs)
                                    }
                                )
                            }
                            composable<PlaylistSongs> {
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

                        navigation<SearchGraph>(
                            startDestination = SearchEntries,
                            popExitTransition = {
                                topBarText = null
                                fadeOut(animationSpec = tween(100))
                            },
                        ) {
                            composable<SearchEntries> {
                                Search(
                                    onPlaylistClicked = { playlist ->
                                        currentPlaylist = playlist as Playlist
                                        navBarController.navigate(PlaylistSongs)
                                    },
                                    onQueryChanged = { query ->
                                        topBarText = query.ifEmpty { searchText }
                                    }
                                )
                            }
                        }

                        composable<ManagePlaylist> {
                            ManagePlaylist(
                                playlist = currentPlaylist,
                                onPlaylistChanged = { playlist ->
                                    currentPlaylist = playlist
                                }
                            )
                        }
                    }
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
            val currentRoute by navBarController.currentBackStackEntryAsState()
            val destination = currentRoute?.destination
            if (destination?.hasRoute<SearchEntries>() == true)
                FloatingActionButton(
                    onClick = {
                        navBarController.navigateUp()
                        topBarText = null
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = searchTooltip
                    )
                }
            else if (destination?.hasRoute<ManagePlaylist>() == true)
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            PlaylistRepository.insert(currentPlaylist)
                            allSongs = SongRepository.getAllSongs()
                            currentPlaylist = Playlist(
                                songs = allSongs,
                            )
                            navBarController.navigateUp()
                            topBarText = null
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = searchTooltip
                    )
                }
            else if (destination?.hasRoute<PlaylistSongs>() == true)
                FloatingActionButton(
                    onClick = {
                        navBarController.navigate(ManagePlaylist)
                        topBarText = addPlaylistText
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = searchTooltip
                    )
                }
            else
                FloatingActionButton(
                    onClick = {
                        navBarController.navigate(SearchGraph)
                        topBarText = searchText
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
