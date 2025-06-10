package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
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
import dr.ulysses.Logger
import dr.ulysses.entities.*
import dr.ulysses.models.MainViewModel
import dr.ulysses.network.NetworkManager.currentServer
import dr.ulysses.player.Player
import dr.ulysses.ui.components.*
import dr.ulysses.ui.elements.LoadingIndicator
import dr.ulysses.ui.elements.SettingsDropdownEntry
import dr.ulysses.ui.permissions.PermissionsAlert
import kotlinx.coroutines.*
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
    val manageUnsortedText = stringResource(Res.string.manage_unsorted)
    val editText = stringResource(Res.string.edit)
    val connectionsText = stringResource(Res.string.connections)
    val refreshSongsText = stringResource(Res.string.refresh_songs)
    val settingsText = stringResource(Res.string.settings)
    val searchTooltip = stringResource(Res.string.search_tooltip)
    val permissionsGranted = rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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

    // Load songs from the server if connected, otherwise from a local repository
    LaunchedEffect(serverConnection) {
        // Use MainViewModel to load songs
        MainViewModel.loadSongs()
    }
    var currentArtistSongsList by remember { mutableStateOf(emptyList<Song>()) }
    var currentAlbumSongsList by remember { mutableStateOf(emptyList<Song>()) }
    var currentPlaylist by remember {
        mutableStateOf(
            Playlist(
                songs = allSongs
            )
        )
    }
    var selectedSong by remember { mutableStateOf<Song?>(null) }
    var currentPlaylists by remember { mutableStateOf(emptyList<Playlist>()) }
    var isLoadingPlaylists by remember { mutableStateOf(true) }
    PermissionsAlert(
        permissionsGranted = permissionsGranted.value,
        onPermissionsChange = {
            permissionsGranted.value = it
        }
    )

    // Observe MainViewModel state
    LaunchedEffect(Unit) {
        // Update local state from MainViewModel state
        snapshotFlow { MainViewModel.state }.collect { viewModelState ->
            allSongs = viewModelState.allSongs
            isLoadingSongs = viewModelState.isLoadingSongs
            selectedSong = viewModelState.selectedSong
            currentArtistSongsList = viewModelState.currentArtistSongsList
            currentAlbumSongsList = viewModelState.currentAlbumSongsList
            currentPlaylist = viewModelState.currentPlaylist
            currentPlaylists = viewModelState.currentPlaylists
            isLoadingPlaylists = viewModelState.isLoadingPlaylists
        }
    }
    val navGraph = navBarController.createGraph(
        startDestination = SongsList
    ) {
        AddNavigationGraph(
            navBarController = navBarController,
            setTopBarText = { topBarText = it },
            pagerState = pagerState,
            onPlaySongCommand = { song ->
                Player.onPlaySongCommand(song)
            },
            onPlaylistChanged = Player::onPlaylistChanged,
            currentPlaylist = currentPlaylist,
            onCurrentPlaylistChanged = { currentPlaylist = it },
            searchText = searchText,
            currentArtistSongsList = currentArtistSongsList,
            currentAlbumSongsList = currentAlbumSongsList
        )
    }

    navBarController.setViewModelStore(LocalViewModelStoreOwner.current?.viewModelStore!!)
    navBarController.setGraph(navGraph, null)

    // Reset returningFromDetail when the user changes tabs
    LaunchedEffect(pagerState.currentPage) {
        if (topBarText == null) { // Only reset if we're not in a detail view
            returningFromDetail = false
            MainViewModel.setReturningFromDetail(false)
        }
    }

    Scaffold(
        topBar = {
            TabMenu(
                pagerState = pagerState, // TODO: pagerState vs dynamic tabs here
                topText = topBarText,
                tabs = mapOf(
                    0 to stringResource(Res.string.artists),
                    1 to stringResource(Res.string.songs),
                    2 to stringResource(Res.string.albums),
                    3 to stringResource(Res.string.playlists),
                ),
                navigateUp = {
                    navBarController.navigateUp()
                    topBarText = null
                    MainViewModel.setTopBarText(null)
                    // Set returning from detail to preserve scroll position
                    returningFromDetail = true
                    MainViewModel.setReturningFromDetail(true)
                    // Restore the previous tab index when navigating back
                    scope.launch {
                        pagerState.scrollToPage(previousTabIndex)
                    }
                },
                menuEntries = listOf(
                    SettingsDropdownEntry(
                        text = addPlaylistText,
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        onClick = {
                            previousTabIndex = pagerState.currentPage // Save current tab index
                            MainViewModel.setPreviousTabIndex(pagerState.currentPage)
                            navBarController.navigate(ManagePlaylist)
                            topBarText = addPlaylistText
                            MainViewModel.setTopBarText(addPlaylistText)
                        }
                    ),
                    SettingsDropdownEntry(
                        text = manageUnsortedText,
                        icon = Icons.Default.EditNote,
                        onClick = {
                            previousTabIndex = pagerState.currentPage // Save current tab index
                            MainViewModel.setPreviousTabIndex(pagerState.currentPage)
                            navBarController.navigate(ManageUnsortedList)
                            topBarText = manageUnsortedText
                            MainViewModel.setTopBarText(manageUnsortedText)
                        }
                    ),
                    SettingsDropdownEntry(
                        text = connectionsText,
                        icon = Icons.Default.Link,
                        onClick = {
                            previousTabIndex = pagerState.currentPage // Save current tab index
                            MainViewModel.setPreviousTabIndex(pagerState.currentPage)
                            navBarController.navigate(Connections)
                            topBarText = connectionsText
                            MainViewModel.setTopBarText(connectionsText)
                        }
                    ),
                    SettingsDropdownEntry(
                        text = refreshSongsText,
                        icon = Icons.Default.Refresh,
                        onClick = {
                            // Call MainViewModel.loadSongs() to refresh the songs
                            MainViewModel.loadSongs()
                        }
                    ),
                    SettingsDropdownEntry(
                        text = settingsText,
                        icon = Icons.Default.Settings,
                        onClick = {
                            previousTabIndex = pagerState.currentPage // Save current tab index
                            MainViewModel.setPreviousTabIndex(pagerState.currentPage)
                            navBarController.navigate(Settings)
                            topBarText = settingsText
                            MainViewModel.setTopBarText(settingsText)
                        }
                    )
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
                    if (topBarText != null) {
                        NavHost(
                            modifier = Modifier.fillMaxSize(),
                            navController = navBarController,
                            graph = navGraph
                        )
                    } else {
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
                                        MainViewModel.setPreviousTabIndex(0)
                                        topBarText = artist
                                        MainViewModel.setTopBarText(artist)
                                        returningFromDetail = false // Reset when navigating to detail
                                        MainViewModel.setReturningFromDetail(false)
                                        val filteredSongs = allSongs.filter {
                                            it.artist.trim().equals(artist.trim(), ignoreCase = true)
                                        }
                                        currentArtistSongsList = filteredSongs
                                        MainViewModel.setCurrentArtistSongsList(filteredSongs)
                                        navBarController.navigate(ArtistSongs)
                                    }
                                )
                            }

                            1 -> if (isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                SongsList(
                                    songs = allSongs,
                                    onClick = { song ->
                                        Player.onSongsChanged(allSongs)
                                        Player.onPlaySongCommand(song)
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
                                        MainViewModel.setPreviousTabIndex(2)
                                        topBarText = album
                                        MainViewModel.setTopBarText(album)
                                        returningFromDetail = false // Reset when navigating to detail
                                        MainViewModel.setReturningFromDetail(false)
                                        val filteredSongs = allSongs.filter {
                                            it.album != null && it.album.equals(album, ignoreCase = true)
                                        }
                                        currentAlbumSongsList = filteredSongs
                                        MainViewModel.setCurrentAlbumSongsList(filteredSongs)
                                        navBarController.navigate(AlbumSongs)
                                    }
                                )
                            }

                            3 -> {
                                if (currentPlaylists.isEmpty()) {
                                    // Use MainViewModel to load playlists
                                    MainViewModel.loadPlaylists()
                                }

                                if (isLoadingPlaylists) {
                                    LoadingIndicator()
                                } else {
                                    PlaylistsList(
                                        playlists = currentPlaylists,
                                        listState = playlistsListState,
                                        initialLoad = !returningFromDetail,
                                        onPlaylistsChanged = {
                                            // Use MainViewModel to load playlists
                                            MainViewModel.loadPlaylists()
                                        },
                                        onPlaylistClicked = { playlist ->
                                            previousTabIndex = 3 // Save Playlists tab index
                                            MainViewModel.setPreviousTabIndex(3)
                                            currentPlaylist = playlist
                                            MainViewModel.setCurrentPlaylist(playlist)
                                            topBarText = playlist.name
                                            MainViewModel.setTopBarText(playlist.name)
                                            returningFromDetail = false // Reset when navigating to detail
                                            MainViewModel.setReturningFromDetail(false)
                                            navBarController.navigate(PlaylistSongs)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = { Player() },
        floatingActionButton = {
            val currentRoute by navBarController.currentBackStackEntryAsState()
            val destination = currentRoute?.destination
            when {
                destination?.hasRoute<SearchEntries>() == true ->
                    FloatingActionButton(
                        onClick = {
                            navBarController.navigateUp()
                            topBarText = null
                            MainViewModel.setTopBarText(null)
                            // Set returning from detail to preserve scroll position
                            returningFromDetail = true
                            MainViewModel.setReturningFromDetail(true)
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

                destination?.hasRoute<ManagePlaylist>() == true ->
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                PlaylistRepository.insert(currentPlaylist)
                                val songs = SongRepository.getAllSongs()
                                allSongs = songs
                                MainViewModel.loadSongs() // This will update allSongs in the ViewModel
                                val newPlaylist = Playlist(songs = songs)
                                currentPlaylist = newPlaylist
                                MainViewModel.setCurrentPlaylist(newPlaylist)
                                navBarController.navigateUp()
                                topBarText = null
                                MainViewModel.setTopBarText(null)
                                // Set returning from detail to preserve scroll position
                                returningFromDetail = true
                                MainViewModel.setReturningFromDetail(true)
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

                destination?.hasRoute<ManageSong>() == true -> {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                selectedSong?.let { selected ->
                                    onSongSave(selected).onSuccess {
                                        SongRepository.upsert(it)
                                        selectedSong = null
                                    }.onFailure {
                                        Logger.e(it) {
                                            it.message ?: it.toString()
                                        }
                                    }
                                }
                                CoroutineScope(Dispatchers.Main).launch {
                                    allSongs = SongRepository.getAllSongs()
                                }
                                MainViewModel.loadSongs() // This will update allSongs in the ViewModel
                                val newPlaylist = Playlist(songs = allSongs)
                                currentPlaylist = newPlaylist
                                MainViewModel.setCurrentPlaylist(newPlaylist)
                                navBarController.navigateUp()
                                topBarText = null
                                MainViewModel.setTopBarText(null)
                                // Set returning from detail to preserve scroll position
                                returningFromDetail = true
                                MainViewModel.setReturningFromDetail(true)
                                // Restore the previous tab index when navigating back from playlist management
                                pagerState.scrollToPage(previousTabIndex)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = editText
                        )
                    }
                }

                destination?.hasRoute<ManageUnsortedList>() == true -> {
                    UnsortedFabMenu()
                }

                destination?.hasRoute<Settings>() == true -> {
                    // Get whether SongsPath was changed and pending jobs from MainViewModel
                    val songsPathChanged = MainViewModel.state.songsPathChanged
                    val pendingSaveJobs = MainViewModel.state.pendingSaveJobs

                    FloatingActionButton(
                        onClick = {
                            // Wait for all pending save operations to complete before navigating
                            scope.launch {
                                // If there are pending save jobs, wait for them to complete
                                if (pendingSaveJobs.isNotEmpty()) {
                                    pendingSaveJobs.forEach { it.join() }
                                }

                                // Now that all settings are saved, navigate back
                                navBarController.navigateUp()
                                topBarText = null
                                MainViewModel.setTopBarText(null)

                                // Restore the previous tab index when navigating back from playlist management
                                pagerState.scrollToPage(previousTabIndex)

                                // Only refresh songs if SongsPath was changed
                                if (songsPathChanged) {
                                    // Refresh songs in the background
                                    CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                                        val refreshedSongs = refreshSongs()

                                        // Update UI on the main thread after refresh completes
                                        withContext(Dispatchers.Main) {
                                            allSongs = refreshedSongs
                                            MainViewModel.loadSongs() // This will update allSongs in the ViewModel
                                            val newPlaylist = Playlist(songs = allSongs)
                                            currentPlaylist = newPlaylist
                                            MainViewModel.setCurrentPlaylist(newPlaylist)
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = searchTooltip
                        )
                    }
                }

                pagerState.currentPage == 3 ->
                    FloatingActionButton(
                        onClick = {
                            // We're already in PlaylistSongs, so the previousTabIndex is already set to 3
                            navBarController.navigate(ManagePlaylist)
                            topBarText = addPlaylistText
                            MainViewModel.setTopBarText(addPlaylistText)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = addPlaylistText
                        )
                    }

                else ->
                    FloatingActionButton(
                        onClick = {
                            previousTabIndex = pagerState.currentPage // Save current tab index
                            MainViewModel.setPreviousTabIndex(pagerState.currentPage)
                            navBarController.navigate(SearchGraph)
                            topBarText = searchText
                            MainViewModel.setTopBarText(searchText)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = searchTooltip
                        )
                    }
            }
        }
    )
}
