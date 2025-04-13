package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import dr.ulysses.network.NetworkManager.currentServer
import dr.ulysses.network.NetworkManager.fetchSongsFromCurrentServer
import dr.ulysses.ui.components.*
import dr.ulysses.ui.elements.LoadingIndicator
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
    var previousTabIndex by remember { mutableStateOf(1) } // Default to Songs tab

    // Remember scroll states for each list
    val artistsListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()

    // Track whether we're returning from a detail view
    var returningFromDetail by remember { mutableStateOf(false) }
    val navBarController = rememberNavController()
    var allSongs by remember { mutableStateOf(emptyList<Song>()) }
    var isLoadingSongs by remember { mutableStateOf(true) }
    // Observe the current server connection status
    val serverConnection by currentServer.collectAsState()

    // Load songs from server if connected, otherwise from local repository
    LaunchedEffect(serverConnection) {
        scope.launch {
            isLoadingSongs = true
            // Check if connected to a server
            allSongs = if (serverConnection != null) {
                // The Client is connected to server, get songs from server
                val serverSongs = fetchSongsFromCurrentServer()
                serverSongs ?: SongRepository.getAllSongs()
            } else {
                // Not connected to server, get songs from local repository
                SongRepository.getAllSongs()
            }
            isLoadingSongs = false
        }
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
    var isLoadingPlaylists by remember { mutableStateOf(true) }
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

    // Reset returningFromDetail when the user changes tabs
    LaunchedEffect(pagerState.currentPage) {
        if (topBarText == null) { // Only reset if we're not in a detail view
            returningFromDetail = false
        }
    }

    Scaffold(
        topBar = {
            TabMenu(
                pagerState = pagerState,
                topText = topBarText,
                navigateUp = {
                    navBarController.navigateUp()
                    topBarText = null
                    // Set returning from detail to preserve scroll position
                    returningFromDetail = true
                    // Restore the previous tab index when navigating back
                    scope.launch {
                        pagerState.scrollToPage(previousTabIndex)
                    }
                },
                tabs = mapOf(
                    0 to stringResource(Res.string.artists),
                    1 to stringResource(Res.string.songs),
                    2 to stringResource(Res.string.albums),
                    3 to stringResource(Res.string.playlists),
                ),
                menuEntries = listOf(
                    addPlaylistText to {
                        previousTabIndex = pagerState.currentPage // Save current tab index
                        navBarController.navigate(ManagePlaylist)
                        topBarText = addPlaylistText
                    },
                    connectionsText to {
                        previousTabIndex = pagerState.currentPage // Save current tab index
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
                            0 -> if (isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                ArtistsList(
                                    artists = allSongs
                                        .map { it.artist.trim() }
                                        .fastDistinctBy(String::lowercase),
                                    onArtistsChanged = {},
                                    listState = artistsListState,
                                    initialLoad = !returningFromDetail,
                                    onArtistClicked = { artist ->
                                        previousTabIndex = 0 // Save Artists tab index
                                        topBarText = artist
                                        returningFromDetail = false // Reset when navigating to detail
                                        currentArtistSongsList = allSongs.filter {
                                            it.artist.trim().lowercase() == artist.trim().lowercase()
                                        }
                                        navBarController.navigate(ArtistSongs)
                                    }
                                )
                            }

                            1 -> if (isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                SongsList(
                                    songs = allSongs,
                                    onPlaySongCommand = { song ->
                                        playerModel.onSongsChanged(allSongs)
                                        playerModel.onPlaySongCommand(song)
                                    }
                                )
                            }

                            2 -> if (isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                AlbumsList(
                                    albums = allSongs
                                        .mapNotNull { it.album?.trim() }
                                        .fastDistinctBy(String::lowercase),
                                    listState = albumsListState,
                                    initialLoad = !returningFromDetail,
                                    onAlbumClicked = { album ->
                                        previousTabIndex = 2 // Save Albums tab index
                                        topBarText = album
                                        returningFromDetail = false // Reset when navigating to detail
                                        currentAlbumSongsList = allSongs.filter {
                                            it.album != null && topBarText != null &&
                                                    it.album.lowercase() == topBarText!!.lowercase()
                                        }
                                        navBarController.navigate(AlbumSongs)
                                    }
                                )
                            }

                            3 -> {
                                if (currentPlaylists.isEmpty()) {
                                    scope.launch {
                                        isLoadingPlaylists = true
                                        currentPlaylists = PlaylistRepository.getAllPlaylists()
                                        isLoadingPlaylists = false
                                    }
                                }

                                if (isLoadingPlaylists) {
                                    LoadingIndicator()
                                } else {
                                    PlaylistsList(
                                        playlists = currentPlaylists,
                                        listState = playlistsListState,
                                        initialLoad = !returningFromDetail,
                                        onPlaylistsChanged = {
                                            scope.launch {
                                                isLoadingPlaylists = true
                                                currentPlaylists = PlaylistRepository.getAllPlaylists()
                                                isLoadingPlaylists = false
                                            }
                                        },
                                        onPlaylistClicked = { playlist ->
                                            previousTabIndex = 3 // Save Playlists tab index
                                            currentPlaylist = playlist
                                            topBarText = currentPlaylist.name
                                            returningFromDetail = false // Reset when navigating to detail
                                            navBarController.navigate(PlaylistSongs)
                                        }
                                    )
                                }
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
                        // Set returning from detail to preserve scroll position
                        returningFromDetail = true
                        // Restore the previous tab index when navigating back from search
                        scope.launch {
                            pagerState.scrollToPage(previousTabIndex)
                        }
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
                            // Set returning from detail to preserve scroll position
                            returningFromDetail = true
                            // Restore the previous tab index when navigating back from playlist management
                            scope.launch {
                                pagerState.scrollToPage(previousTabIndex)
                            }
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
                        // We're already in PlaylistSongs, so previousTabIndex is already set to 3
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
                        previousTabIndex = pagerState.currentPage // Save current tab index
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
