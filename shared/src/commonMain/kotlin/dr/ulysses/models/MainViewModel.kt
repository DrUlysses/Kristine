package dr.ulysses.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import dr.ulysses.Logger
import dr.ulysses.api.internal.LibraryClient
import dr.ulysses.api.internal.isRemote
import dr.ulysses.entities.*
import dr.ulysses.network.NetworkManager
import dr.ulysses.player.Player
import dr.ulysses.ui.views.AlbumRef
import dr.ulysses.ui.views.forgetArtwork
import dr.ulysses.ui.views.onSongSave
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop

/**
 * The single source of the Main screen's state; it outlives navigation.
 */
object MainViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Every new state counts as a change: Song.equals ignores artwork, so an artwork-only edit would be dropped
    var state: MainState by mutableStateOf(
        value = MainState(),
        policy = referentialEqualityPolicy()
    )
        private set

    init {
        loadSongs()
        // Connecting and disconnecting switch between the server's library and this device's, also while the
        // window isn't drawn
        scope.launch {
            NetworkManager.isConnected.drop(1).collect {
                loadSongs()
                loadPlaylists()
            }
        }
    }

    /**
     * The connected server's library, otherwise this device's.
     */
    fun loadSongs() {
        scope.launch {
            state = state.copy(
                isLoadingSongs = true
            )
            val songs = SongRepository.getAllSongs()
            state = state.copy(
                allSongs = songs,
                isLoadingSongs = false
            )
        }
    }

    /**
     * Scans the music folder for new files, the connected server's when [onServer], then reloads.
     */
    fun scanLibrary(onServer: Boolean = isRemote) {
        scope.launch {
            if (onServer) {
                LibraryClient.scan()
            } else {
                withContext(Dispatchers.Default) { refreshSongs() }
            }
            loadSongs()
        }
    }

    fun loadPlaylists() {
        scope.launch {
            state = state.copy(
                isLoadingPlaylists = true
            )
            state = state.copy(
                currentPlaylists = PlaylistRepository.getAllPlaylists(),
                isLoadingPlaylists = false
            )
        }
    }

    /**
     * The artwork of a listed song: from the connected server, otherwise from this device.
     */
    suspend fun artwork(path: String): ByteArray? = if (isRemote) {
        NetworkManager.remoteArtwork(path)
    } else {
        SongRepository.getArtwork(path)
    }

    suspend fun playlistSongs(name: String): List<Song> = PlaylistRepository.getPlaylistSongs(name)

    /**
     * The DB keeps the edit even when the file can't be written, e.g. without storage permission.
     * Saves the song's tags, marked sorted unless [sort] is false, and files a sorted song in <music folder>/Artist/
     * Album/Title like Organize folders; returns the song at its new path.
     */
    suspend fun saveSong(
        song: Song,
        sort: Boolean = true,
    ): Song = withContext(Dispatchers.Default) {
        if (isRemote) {
            withContext(Dispatchers.Main) { forgetArtwork(song.path) }
            return@withContext LibraryClient.saveSong(
                song = song,
                sort = sort
            )
        }
        val sorted = if (sort) {
            song.copy(
                state = Song.State.Sorted
            )
        } else {
            song
        }
        SongRepository.upsert(sorted)
        withContext(Dispatchers.Main) { forgetArtwork(song.path) } // the cache is only touched on the UI thread
        onSongSave(song).onFailure { Logger.e(it) { "Couldn't write the tags of ${song.path}" } }
        val path = if (sorted.state == Song.State.Sorted) {
            fileSorted(sorted)
        } else {
            song.path
        }
        if (path != song.path) {
            // Its old path is gone
            withContext(Dispatchers.Main) { Player.onRemoveFromQueueCommand(song) }
        }
        sorted.copy(
            path = path
        )
    }

    fun openDetail(
        title: String,
        fromTab: Int,
    ) {
        state = state.copy(
            topBarText = title,
            previousTabIndex = fromTab,
            returningFromDetail = false
        )
    }

    fun closeDetail() {
        state = state.copy(
            topBarText = null,
            returningFromDetail = true
        )
    }

    fun setTopBarText(text: String?) {
        state = state.copy(
            topBarText = text
        )
    }

    fun setReturningFromDetail(returning: Boolean) {
        state = state.copy(
            returningFromDetail = returning
        )
    }

    fun setCurrentArtist(artist: String) {
        state = state.copy(
            currentArtist = artist
        )
    }

    fun setCurrentAlbum(album: AlbumRef) {
        state = state.copy(
            currentAlbum = album
        )
    }

    fun setCurrentPlaylist(playlist: Playlist) {
        state = state.copy(
            currentPlaylist = playlist
        )
    }

    fun setSelectedSong(song: Song?) {
        state = state.copy(
            selectedSong = song
        )
    }

    fun setPendingSaveJobs(jobs: List<Job>) {
        state = state.copy(
            pendingSaveJobs = jobs
        )
    }

    data class MainState(
        val allSongs: List<Song> = emptyList(),
        val selectedSong: Song? = null,
        val isLoadingSongs: Boolean = true,
        val currentArtist: String = "",
        val currentAlbum: AlbumRef = AlbumRef(
            title = "",
            artist = ""
        ),
        val currentPlaylist: Playlist = Playlist(),
        val currentPlaylists: List<Playlist> = emptyList(),
        val isLoadingPlaylists: Boolean = true,
        val topBarText: String? = null,
        val previousTabIndex: Int = 1, // Songs tab
        val returningFromDetail: Boolean = false,
        val pendingSaveJobs: List<Job> = emptyList(),
    )
}
