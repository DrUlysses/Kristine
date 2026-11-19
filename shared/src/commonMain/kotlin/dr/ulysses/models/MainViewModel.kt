package dr.ulysses.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import dr.ulysses.Logger
import dr.ulysses.entities.*
import dr.ulysses.network.NetworkManager
import dr.ulysses.ui.views.forgetArtwork
import dr.ulysses.ui.views.onSongSave
import kotlinx.coroutines.*

/** The single source of the Main screen's state; it outlives navigation. */
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
    }

    /** The connected server's library, otherwise this device's. */
    fun loadSongs() {
        scope.launch {
            state = state.copy(
                isLoadingSongs = true
            )
            val songs = remoteLibrary?.songs ?: SongRepository.getAllSongs()
            state = state.copy(
                allSongs = songs,
                isLoadingSongs = false
            )
        }
    }

    /** Scans the music folder for new files, then reloads. */
    fun scanLibrary() {
        scope.launch {
            withContext(Dispatchers.Default) { refreshSongs() }
            loadSongs()
        }
    }

    fun loadPlaylists() {
        scope.launch {
            state = state.copy(
                isLoadingPlaylists = true
            )
            state = state.copy(
                currentPlaylists = remoteLibrary?.playlists ?: PlaylistRepository.getAllPlaylists(),
                isLoadingPlaylists = false
            )
        }
    }

    /** The artwork of a listed song: from the connected server, otherwise from this device. */
    suspend fun artwork(path: String): ByteArray? = if (isRemote) {
        NetworkManager.remoteArtwork(path)
    } else {
        SongRepository.getArtwork(path)
    }

    /** The lists show the connected server's library. */
    val isRemote get() = remoteLibrary != null

    suspend fun playlistSongs(name: String): List<Song> =
        remoteLibrary?.playlists?.find { it.name == name }?.songs ?: PlaylistRepository.getPlaylistSongs(name)

    private val remoteLibrary get() = NetworkManager.remoteLibrary.value

    /** The DB keeps the edit even when the file can't be written, e.g. without storage permission. */
    suspend fun saveSong(song: Song) = withContext(Dispatchers.Default) {
        SongRepository.upsert(
            song.copy(
                state = Song.State.Sorted
            )
        )
        withContext(Dispatchers.Main) { forgetArtwork(song.path) } // the cache is only touched on the UI thread
        onSongSave(song).onFailure { Logger.e(it) { "Couldn't write the tags of ${song.path}" } }
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

    fun setCurrentArtistSongsList(songs: List<Song>) {
        state = state.copy(
            currentArtistSongsList = songs
        )
    }

    fun setCurrentAlbumSongsList(songs: List<Song>) {
        state = state.copy(
            currentAlbumSongsList = songs
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

    fun setSongsPathChanged(changed: Boolean) {
        state = state.copy(
            songsPathChanged = changed
        )
    }

    data class MainState(
        val allSongs: List<Song> = emptyList(),
        val selectedSong: Song? = null,
        val isLoadingSongs: Boolean = true,
        val currentArtistSongsList: List<Song> = emptyList(),
        val currentAlbumSongsList: List<Song> = emptyList(),
        val currentPlaylist: Playlist = Playlist(),
        val currentPlaylists: List<Playlist> = emptyList(),
        val isLoadingPlaylists: Boolean = true,
        val topBarText: String? = null,
        val previousTabIndex: Int = 1, // Songs tab
        val returningFromDetail: Boolean = false,
        val pendingSaveJobs: List<Job> = emptyList(),
        val songsPathChanged: Boolean = false,
    )
}
