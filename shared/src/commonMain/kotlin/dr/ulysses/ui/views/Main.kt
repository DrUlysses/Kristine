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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dr.ulysses.Logger
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.models.MainViewModel
import dr.ulysses.network.NetworkManager
import dr.ulysses.player.Player
import dr.ulysses.ui.components.*
import dr.ulysses.ui.elements.LoadingIndicator
import dr.ulysses.ui.elements.SettingsDropdownEntry
import dr.ulysses.ui.permissions.PermissionsAlert
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kristine.shared.generated.resources.*
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
    // Read in composition; lambdas that run later read MainViewModel.state for the current value
    val state = MainViewModel.state

    val artistsListState = rememberLazyListState()
    val albumsListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()

    val navStack = rememberNavBackStack(config, Songs)
    var pendingRoute by remember { mutableStateOf<NavKey?>(null) }
    val serverConnection by NetworkManager.currentServer.collectAsState()

    LaunchedEffect(serverConnection) {
        MainViewModel.loadSongs()
    }

    PermissionsAlert(
        permissionsGranted = permissionsGranted.value,
        onPermissionsChange = { permissionsGranted.value = it }
    )

    // The NavDisplay is only composed while a title is shown, so navigation waits for it
    LaunchedEffect(state.topBarText, pendingRoute) {
        val route = pendingRoute
        if (state.topBarText != null && route != null) {
            navStack.add(route)
            pendingRoute = null
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (state.topBarText == null) MainViewModel.setReturningFromDetail(false)
    }

    fun open(title: String, route: NavKey) {
        MainViewModel.openDetail(title, fromTab = pagerState.currentPage)
        pendingRoute = route
    }

    fun close() {
        navStack.removeLastOrNull()
        MainViewModel.closeDetail()
        scope.launch { pagerState.scrollToPage(MainViewModel.state.previousTabIndex) }
    }

    fun addPlaylist() {
        MainViewModel.setCurrentPlaylist(Playlist())
        open(addPlaylistText, ManagePlaylist)
    }

    Scaffold(
        topBar = {
            TabMenu(
                pagerState = pagerState, // TODO: pagerState vs dynamic tabs here
                topText = state.topBarText,
                tabs = mapOf(
                    0 to stringResource(Res.string.artists),
                    1 to stringResource(Res.string.songs),
                    2 to stringResource(Res.string.albums),
                    3 to stringResource(Res.string.playlists),
                ),
                navigateUp = ::close,
                menuEntries = listOf(
                    SettingsDropdownEntry(
                        text = addPlaylistText,
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        onClick = ::addPlaylist
                    ),
                    SettingsDropdownEntry(
                        text = manageUnsortedText,
                        icon = Icons.Default.EditNote,
                        onClick = { open(manageUnsortedText, ManageUnsortedList) }
                    ),
                    SettingsDropdownEntry(
                        text = connectionsText,
                        icon = Icons.Default.Link,
                        onClick = { open(connectionsText, Connections) }
                    ),
                    SettingsDropdownEntry(
                        text = refreshSongsText,
                        icon = Icons.Default.Refresh,
                        onClick = MainViewModel::scanLibrary
                    ),
                    SettingsDropdownEntry(
                        text = settingsText,
                        icon = Icons.Default.Settings,
                        onClick = { open(settingsText, Settings) }
                    )
                )
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = pagerState
                ) { page ->
                    if (state.topBarText != null) {
                        NavDisplay(
                            modifier = Modifier.fillMaxSize(),
                            backStack = navStack,
                            onBack = { navStack.removeLastOrNull() },
                            entryProvider = entryProvider {
                                entry<Artists> {
                                    LaunchedEffect(Unit) { pagerState.scrollToPage(0) }
                                }
                                entry<Songs> {
                                    LaunchedEffect(Unit) { pagerState.scrollToPage(1) }
                                }
                                entry<Albums> {
                                    LaunchedEffect(Unit) { pagerState.scrollToPage(2) }
                                }
                                entry<Playlists> {
                                    LaunchedEffect(Unit) { pagerState.scrollToPage(3) }
                                }
                                entry<ArtistSongs> {
                                    SongsList(
                                        songs = state.currentArtistSongsList,
                                        onClick = { song ->
                                            Player.onSongsChanged(state.currentArtistSongsList)
                                            Player.onPlaySongCommand(song)
                                        }
                                    )
                                }
                                entry<AlbumSongs> {
                                    SongsList(
                                        songs = state.currentAlbumSongsList,
                                        onClick = { song ->
                                            Player.onSongsChanged(state.currentAlbumSongsList)
                                            Player.onPlaySongCommand(song)
                                        }
                                    )
                                }
                                entry<PlaylistSongs> {
                                    var songs by remember { mutableStateOf(emptyList<Song>()) }
                                    LaunchedEffect(state.currentPlaylist.name) {
                                        songs = PlaylistRepository.getPlaylistSongs(state.currentPlaylist.name)
                                    }
                                    SongsList(
                                        songs = songs,
                                        onClick = { song ->
                                            Player.onSongsChanged(songs)
                                            Player.onPlaySongCommand(song)
                                        }
                                    )
                                }
                                entry<Search> {
                                    Search(
                                        onPlaylistClicked = { playlist ->
                                            playlist as Playlist
                                            MainViewModel.setCurrentPlaylist(playlist)
                                            MainViewModel.setTopBarText(playlist.name)
                                            navStack.add(PlaylistSongs)
                                        },
                                        onQueryChanged = { query ->
                                            MainViewModel.setTopBarText(query.ifEmpty { searchText })
                                        }
                                    )
                                }
                                entry<ManagePlaylist> {
                                    ManagePlaylist(
                                        playlist = state.currentPlaylist,
                                        onPlaylistChanged = MainViewModel::setCurrentPlaylist
                                    )
                                }
                                entry<ManageUnsortedList> {
                                    var unsortedSongs by remember { mutableStateOf(emptyList<Song>()) }
                                    LaunchedEffect(Unit) {
                                        unsortedSongs = SongRepository.getByNotState(Song.State.Sorted)
                                    }
                                    if (unsortedSongs.isNotEmpty()) {
                                        ManageUnsortedList(
                                            unsortedSongs = unsortedSongs,
                                            onClick = { song ->
                                                MainViewModel.setSelectedSong(song)
                                                navStack.add(ManageSong)
                                            }
                                        )
                                    } else {
                                        LoadingIndicator()
                                    }
                                }
                                entry<ManageSong> {
                                    var unsortedSongs by rememberSaveable { mutableStateOf(emptyList<Song>()) }
                                    LaunchedEffect(Unit) {
                                        unsortedSongs = SongRepository.getByNotState(Song.State.Sorted)
                                    }
                                    val currentSong = state.selectedSong
                                    val currentSongIndex = unsortedSongs.indexOf(currentSong)
                                    if (currentSong != null && unsortedSongs.isNotEmpty()) {
                                        ManageUnsortedSong(
                                            song = currentSong,
                                            unsortedSongs = unsortedSongs,
                                            onSongEdited = { edited ->
                                                scope.launch { MainViewModel.saveSong(edited) }
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
                                entry<Connections> {
                                    Connections()
                                }
                                entry<Settings> {
                                    Settings(
                                        onPendingSaveJobsChanged = MainViewModel::setPendingSaveJobs,
                                        onSongsPathChanged = MainViewModel::setSongsPathChanged
                                    )
                                }
                            }
                        )
                    } else {
                        when (page) {
                            0 -> if (state.isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                ArtistsList(
                                    artists = state.allSongs
                                        .map { it.artist.trim() }
                                        .fastDistinctBy(String::lowercase),
                                    onArtistsChanged = {},
                                    listState = artistsListState,
                                    initialLoad = !state.returningFromDetail,
                                    onArtistClicked = { artist ->
                                        MainViewModel.setCurrentArtistSongsList(state.allSongs.filter {
                                            it.artist.trim().equals(artist.trim(), ignoreCase = true)
                                        })
                                        open(artist, ArtistSongs)
                                    }
                                )
                            }

                            1 -> if (state.isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                SongsList(
                                    songs = state.allSongs,
                                    onClick = { song ->
                                        Player.onSongsChanged(state.allSongs)
                                        Player.onPlaySongCommand(song)
                                    }
                                )
                            }

                            2 -> if (state.isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                AlbumsList(
                                    albums = state.allSongs
                                        .mapNotNull { it.album?.trim() }
                                        .fastDistinctBy(String::lowercase),
                                    listState = albumsListState,
                                    initialLoad = !state.returningFromDetail,
                                    onAlbumClicked = { album ->
                                        MainViewModel.setCurrentAlbumSongsList(state.allSongs.filter {
                                            it.album.equals(album, ignoreCase = true)
                                        })
                                        open(album, AlbumSongs)
                                    }
                                )
                            }

                            3 -> {
                                LaunchedEffect(Unit) { MainViewModel.loadPlaylists() }

                                if (state.isLoadingPlaylists) {
                                    LoadingIndicator()
                                } else {
                                    PlaylistsList(
                                        playlists = state.currentPlaylists,
                                        listState = playlistsListState,
                                        initialLoad = !state.returningFromDetail,
                                        onPlaylistsChanged = { MainViewModel.loadPlaylists() },
                                        onPlaylistClicked = { playlist ->
                                            MainViewModel.setCurrentPlaylist(playlist)
                                            open(playlist.name, PlaylistSongs)
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
            val route = navStack.lastOrNull()
            when {
                route == Search ->
                    FloatingActionButton(onClick = ::close) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = searchTooltip)
                    }

                route == ManagePlaylist ->
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                val playlist = MainViewModel.state.currentPlaylist
                                // Fails when the name is taken; the user stays on the screen to change it
                                runCatching { PlaylistRepository.insert(playlist) }
                                    .onSuccess {
                                        MainViewModel.loadPlaylists()
                                        close()
                                    }
                                    .onFailure { Logger.e(it) { "Saving playlist ${playlist.name} failed" } }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = searchTooltip)
                    }

                route == ManageSong ->
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                MainViewModel.state.selectedSong?.let { MainViewModel.saveSong(it) }
                                MainViewModel.setSelectedSong(null)
                                MainViewModel.loadSongs()
                                close()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = editText)
                    }

                route == ManageUnsortedList -> UnsortedFabMenu()

                route == Settings ->
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                MainViewModel.state.pendingSaveJobs.joinAll()
                                close()
                                if (MainViewModel.state.songsPathChanged) MainViewModel.scanLibrary()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = searchTooltip)
                    }

                pagerState.currentPage == 3 ->
                    FloatingActionButton(onClick = ::addPlaylist) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = addPlaylistText)
                    }

                else ->
                    FloatingActionButton(onClick = { open(searchText, Search) }) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = searchTooltip)
                    }
            }
        }
    )
}
