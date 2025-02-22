package dr.ulysses.ui.views

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
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
    val connectionsText = stringResource(Res.string.connections)
    val searchTooltip = stringResource(Res.string.search_tooltip)
    val permissionsGranted = rememberSaveable { mutableStateOf(false) }
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
                songs = allSongs
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
    val navGraph = navBarController.createGraph(
        startDestination = SongsList
    ) {
        addNavigationGraph(
            navBarController = navBarController,
            setTopBarText = { topBarText = it },
            pagerState = pagerState,
            onPlaySongCommand = playerModel::onPlaySongCommand,
            onPlaylistChanged = playerModel::onPlaylistChanged,
            currentPlaylist = currentPlaylist,
            onCurrentPlaylistChanged = { currentPlaylist = it },
            searchText = searchText,
            currentArtistSongsList = currentArtistSongsList,
            currentAlbumSongsList = currentAlbumSongsList,
        )
    }

    navBarController.setViewModelStore(LocalViewModelStoreOwner.current?.viewModelStore!!)
    navBarController.setGraph(navGraph, null)

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
                ),
                menuEntries = listOf(
                    addPlaylistText to {
                        navBarController.navigate(ManagePlaylist)
                        topBarText = addPlaylistText
                    },
                    connectionsText to {
                        navBarController.navigate(Connections)
                        topBarText = connectionsText
                    }
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
                    state = pagerState
                ) { page ->
                    if (topBarText != null)
                        NavHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navBarController,
                            graph = navGraph
                        )
                    else
                        when (page) {
                            0 -> ArtistsList(
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

                            1 -> SongsList(
                                songs = allSongs,
                                onPlaySongCommand = { song ->
                                    playerModel.onSongsChanged(allSongs)
                                    playerModel.onPlaySongCommand(song)
                                }
                            )

                            2 -> AlbumsList(
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

                            3 -> PlaylistsList(
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
