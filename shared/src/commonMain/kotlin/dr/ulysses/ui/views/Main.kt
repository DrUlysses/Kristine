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
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import dr.ulysses.ui.elements.canManageLibrary
import dr.ulysses.ui.permissions.PermissionsAlert
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kristine.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun Main() {
    val tabs = listOf(
        stringResource(Res.string.artists),
        stringResource(Res.string.songs),
        stringResource(Res.string.albums),
        stringResource(Res.string.playlists),
    )
    val pagerState = rememberPagerState(
        initialPage = 1
    ) { tabs.size }
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

    PermissionsAlert(
        permissionsGranted = permissionsGranted.value,
        onPermissionsChange = { permissionsGranted.value = it }
    )

    // The NavDisplay is only composed while a title is shown, so navigation waits for it
    LaunchedEffect(
        key1 = state.topBarText,
        key2 = pendingRoute
    ) {
        val route = pendingRoute
        if (state.topBarText != null && route != null) {
            navStack.add(route)
            pendingRoute = null
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (state.topBarText == null) {
            MainViewModel.setReturningFromDetail(false)
        }
    }

    fun open(
        title: String,
        route: NavKey,
    ) {
        MainViewModel.openDetail(
            title = title,
            fromTab = pagerState.currentPage
        )
        pendingRoute = route
    }

    fun close() {
        val leaving = navStack.lastOrNull()
        navStack.removeLastOrNull()
        // These open from the unsorted list and go back to it
        if (leaving == ManageDuplicates || leaving == AutoSortSongs || leaving == MergeNames) {
            MainViewModel.setTopBarText(manageUnsortedText)
            return
        }
        // An album opened from its artist goes back to the artist
        if (leaving == AlbumSongs && navStack.lastOrNull() == ArtistSongs) {
            MainViewModel.setTopBarText(MainViewModel.state.currentArtist)
            return
        }
        MainViewModel.closeDetail()
        scope.launch { pagerState.scrollToPage(MainViewModel.state.previousTabIndex) }
        // Tags edited there show up in the lists, like after "Refresh songs"
        if (leaving == ManageSong || leaving == ManageUnsortedList) {
            MainViewModel.scanLibrary()
        }
    }

    fun openAlbum(album: AlbumRef) {
        MainViewModel.setCurrentAlbum(album)
        open(
            title = album.title,
            route = AlbumSongs
        )
    }

    fun addPlaylist() {
        MainViewModel.setCurrentPlaylist(Playlist())
        open(
            title = addPlaylistText,
            route = ManagePlaylist
        )
    }

    val canManage = canManageLibrary()

    NetworkManager.connectionError.collectAsState().value?.let { error ->
        AlertDialog(
            onDismissRequest = NetworkManager::dismissConnectionError,
            title = { Text("Disconnected") },
            text = { Text(error) },
            confirmButton = {
                TextButton(
                    onClick = NetworkManager::dismissConnectionError
                ) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TabMenu(
                pagerState = pagerState,
                topText = state.topBarText,
                tabs = tabs,
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
                        enabled = canManage,
                        onClick = {
                            open(
                                title = manageUnsortedText,
                                route = ManageUnsortedList
                            )
                        }
                    ),
                    SettingsDropdownEntry(
                        text = connectionsText,
                        icon = Icons.Default.Link,
                        onClick = {
                            open(
                                title = connectionsText,
                                route = Connections
                            )
                        }
                    ),
                    SettingsDropdownEntry(
                        text = refreshSongsText,
                        icon = Icons.Default.Refresh,
                        enabled = canManage,
                        onClick = MainViewModel::scanLibrary
                    ),
                    SettingsDropdownEntry(
                        text = settingsText,
                        icon = Icons.Default.Settings,
                        onClick = {
                            open(
                                title = settingsText,
                                route = Settings
                            )
                        }
                    )
                )
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier.padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // A detail view replaces the pager: one NavDisplay, and no swiping tabs underneath it
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
                                ArtistView(
                                    artist = state.currentArtist,
                                    allSongs = state.allSongs,
                                    onAlbumClick = ::openAlbum
                                )
                            }
                            entry<AlbumSongs> {
                                AlbumView(
                                    album = state.currentAlbum,
                                    allSongs = state.allSongs
                                )
                            }
                            entry<PlaylistSongs> {
                                var songs by remember { mutableStateOf(emptyList<Song>()) }
                                LaunchedEffect(state.currentPlaylist.name) {
                                    songs = MainViewModel.playlistSongs(state.currentPlaylist.name)
                                }
                                SongsList(
                                    songs = songs,
                                    onClick = { song ->
                                        Player.startQueue(
                                            songs = songs,
                                            first = song
                                        )
                                    }
                                )
                            }
                            entry<CurrentQueue> {
                                SongsList(
                                    songs = Player.state.currentPlaylist.songs,
                                    // A song of the queue: the player jumps to it instead of starting a new queue
                                    onClick = Player::onPlaySongCommand,
                                    // A server's queue is changed on the server
                                    action = if (Player.state.isRemotePlaying) {
                                        null
                                    } else {
                                        { song ->
                                            IconButton(
                                                onClick = { Player.onRemoveFromQueueCommand(song) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                                    contentDescription = "Remove from queue"
                                                )
                                            }
                                        }
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
                                var unsortedSongs by remember { mutableStateOf<List<Song>?>(null) }
                                // Also after returning from Duplicates, which may have removed some
                                LaunchedEffect(Unit) {
                                    unsortedSongs = SongRepository.getByNotState(Song.State.Sorted)
                                }
                                val songs = unsortedSongs
                                if (songs != null) {
                                    ManageUnsortedList(
                                        unsortedSongs = songs,
                                        onDuplicatesClick = {
                                            MainViewModel.setTopBarText(RESOLVE_DUPLICATES)
                                            navStack.add(ManageDuplicates)
                                        },
                                        onAutoSortClick = {
                                            MainViewModel.setTopBarText(AUTO_SORT)
                                            navStack.add(AutoSortSongs)
                                        },
                                        onMergeNamesClick = {
                                            MainViewModel.setTopBarText(MERGE_NAMES)
                                            navStack.add(MergeNames)
                                        },
                                        onClick = { song ->
                                            MainViewModel.setSelectedSong(song)
                                            navStack.add(ManageSong)
                                        }
                                    )
                                } else {
                                    LoadingIndicator()
                                }
                            }
                            entry<ManageDuplicates> {
                                ManageDuplicates()
                            }
                            entry<AutoSortSongs> {
                                AutoSortView()
                            }
                            entry<MergeNames> {
                                MergeNamesView()
                            }
                            entry<ManageSong> {
                                var unsortedSongs by remember { mutableStateOf(emptyList<Song>()) }
                                LaunchedEffect(Unit) {
                                    val opened = MainViewModel.state.selectedSong
                                    // Only songs with their file; the others wait in Missing files
                                    val unsorted = SongRepository.getByNotState(Song.State.Sorted)
                                        .filter { it.state != Song.State.NotDownloaded }
                                    // A song opened from the player may be sorted already: it comes first, then the unsorted ones
                                    unsortedSongs = if (opened == null || unsorted.any { it.path == opened.path }) {
                                        unsorted
                                    } else {
                                        listOf(opened) + unsorted
                                    }
                                }
                                val currentSong = state.selectedSong
                                // By path: the selected song is a draft whose title/artist may differ from the list's copy
                                val currentSongIndex = unsortedSongs.indexOfFirst { it.path == currentSong?.path }

                                // Another song takes the screen, so the one being left leaves the queue if it plays
                                fun showSong(index: Int) {
                                    if (currentSong != null && Player.state.currentSong?.path == currentSong.path) {
                                        Player.onRemoveFromQueueCommand(currentSong)
                                    }
                                    val next = unsortedSongs.getOrNull(index)
                                    if (next == null) {
                                        // Past the last song there is nothing left to manage
                                        MainViewModel.setSelectedSong(null)
                                        close()
                                        return
                                    }
                                    MainViewModel.setSelectedSong(next)
                                }
                                if (currentSong != null && unsortedSongs.isNotEmpty()) {
                                    ManageUnsortedSong(
                                        song = currentSong,
                                        unsortedSongs = unsortedSongs,
                                        onSongEdited = MainViewModel::setSelectedSong,
                                        onSave = { saved ->
                                            scope.launch {
                                                // At its new path once filed in its Artist / Album folder
                                                val filed = MainViewModel.saveSong(saved)
                                                unsortedSongs = unsortedSongs.map {
                                                    if (it.path == saved.path) {
                                                        filed
                                                    } else {
                                                        it
                                                    }
                                                }
                                                if (MainViewModel.state.selectedSong?.path == saved.path) {
                                                    MainViewModel.setSelectedSong(filed)
                                                }
                                            }
                                        },
                                        onNextSong = {
                                            showSong(currentSongIndex + 1)
                                        },
                                        onPreviousSong = {
                                            showSong(currentSongIndex - 1)
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
                                    onPendingSaveJobsChanged = MainViewModel::setPendingSaveJobs
                                )
                            }
                        }
                    )
                } else {
                    HorizontalPager(
                        modifier = Modifier.fillMaxSize(),
                        state = pagerState
                    ) { page ->
                        when (page) {
                            0 -> if (state.isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                ArtistsList(
                                    library = state.allSongs,
                                    listState = artistsListState,
                                    initialLoad = !state.returningFromDetail,
                                    onArtistClicked = { artist ->
                                        MainViewModel.setCurrentArtist(artist)
                                        open(
                                            title = artist,
                                            route = ArtistSongs
                                        )
                                    }
                                )
                            }

                            1 -> if (state.isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                SongsList(
                                    songs = state.allSongs,
                                    onClick = { song ->
                                        Player.startQueue(
                                            songs = state.allSongs,
                                            first = song
                                        )
                                    }
                                )
                            }

                            2 -> if (state.isLoadingSongs) {
                                LoadingIndicator()
                            } else {
                                AlbumsList(
                                    library = state.allSongs,
                                    listState = albumsListState,
                                    initialLoad = !state.returningFromDetail,
                                    onAlbumClicked = ::openAlbum
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
                                        onQueueClicked = {
                                            open(
                                                title = CURRENT_QUEUE,
                                                route = CurrentQueue
                                            )
                                        }.takeIf { Player.state.currentPlaylist.songs.isNotEmpty() },
                                        onPlaylistClicked = { playlist ->
                                            MainViewModel.setCurrentPlaylist(playlist)
                                            open(
                                                title = playlist.name,
                                                route = PlaylistSongs
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Player(
                onManageSong = { song ->
                    scope.launch {
                        // The player's copy has no artwork; saving it would erase the stored one
                        val stored = SongRepository.getByPathOrNull(song.path) ?: return@launch
                        MainViewModel.setSelectedSong(stored)
                        open(
                            title = editText,
                            route = ManageSong
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            val route = navStack.lastOrNull()
            when {
                route == Search ->
                    FloatingActionButton(
                        onClick = ::close
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = searchTooltip
                        )
                    }

                route == ManagePlaylist ->
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                val playlist = MainViewModel.state.currentPlaylist
                                // Fails when the name is taken; the user stays on the screen to change it
                                runCatching { PlaylistRepository.insert(playlist) }
                                    .onFailure { currentCoroutineContext().ensureActive() }
                                    .onSuccess {
                                        MainViewModel.loadPlaylists()
                                        close()
                                    }
                                    .onFailure { Logger.e(it) { "Saving playlist ${playlist.name} failed" } }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = searchTooltip
                        )
                    }

                route == ManageSong ->
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                MainViewModel.state.selectedSong?.let { MainViewModel.saveSong(it) }
                                MainViewModel.setSelectedSong(null)
                                close()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = editText
                        )
                    }

                route == ManageUnsortedList -> UnsortedFabMenu()

                route == CurrentQueue ->
                    FloatingActionButton(
                        onClick = {
                            // The new playlist screen, with the queue's songs already in it
                            MainViewModel.setCurrentPlaylist(
                                Playlist(
                                    songs = Player.state.currentPlaylist.songs
                                )
                            )
                            open(
                                title = addPlaylistText,
                                route = ManagePlaylist
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Save as playlist"
                        )
                    }

                route == Settings ->
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                MainViewModel.state.pendingSaveJobs.joinAll()
                                close()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = searchTooltip
                        )
                    }

                pagerState.currentPage == 3 ->
                    FloatingActionButton(
                        onClick = ::addPlaylist
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = addPlaylistText
                        )
                    }

                else ->
                    FloatingActionButton(
                        onClick = {
                            open(
                                title = searchText,
                                route = Search
                            )
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
