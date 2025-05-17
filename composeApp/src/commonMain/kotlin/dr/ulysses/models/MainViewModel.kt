package dr.ulysses.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.PlaylistRepository
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.network.NetworkManager.currentServer
import dr.ulysses.network.NetworkManager.fetchSongsFromCurrentServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel for the Main screen that manages UI state
 */
object MainViewModel {
    private val scope = CoroutineScope(Dispatchers.Main)

    var state: MainState by mutableStateOf(MainState())
        private set

    init {
        // Initialize by loading songs
        loadSongs()
    }

    fun loadSongs() {
        scope.launch {
            state = state.copy(isLoadingSongs = true)

            // Check if connected to a server
            val serverConnection = currentServer.value
            val songs = if (serverConnection != null) {
                // The Client is connected to server, get songs from server
                val serverSongs = fetchSongsFromCurrentServer()
                serverSongs ?: SongRepository.getAllSongs()
            } else {
                // Not connected to server, get songs from local repository
                SongRepository.getAllSongs()
            }

            state = state.copy(
                allSongs = songs,
                isLoadingSongs = false
            )
        }
    }

    fun loadPlaylists() {
        scope.launch {
            state = state.copy(isLoadingPlaylists = true)
            val playlists = PlaylistRepository.getAllPlaylists()
            state = state.copy(
                currentPlaylists = playlists,
                isLoadingPlaylists = false
            )
        }
    }

    fun setTopBarText(text: String?) {
        state = state.copy(topBarText = text)
    }

    fun setPreviousTabIndex(index: Int) {
        state = state.copy(previousTabIndex = index)
    }

    fun setReturningFromDetail(returning: Boolean) {
        state = state.copy(returningFromDetail = returning)
    }

    fun setCurrentArtistSongsList(songs: List<Song>) {
        state = state.copy(currentArtistSongsList = songs)
    }

    fun setCurrentAlbumSongsList(songs: List<Song>) {
        state = state.copy(currentAlbumSongsList = songs)
    }

    fun setCurrentPlaylist(playlist: Playlist) {
        state = state.copy(currentPlaylist = playlist)
    }

    fun setSelectedSong(song: Song?) {
        state = state.copy(selectedSong = song)
    }

    fun setPendingSaveJobs(jobs: List<Job>) {
        state = state.copy(pendingSaveJobs = jobs)
    }

    fun setSongsPathChanged(changed: Boolean) {
        state = state.copy(songsPathChanged = changed)
    }

    data class MainState(
        val allSongs: List<Song> = emptyList(),
        val selectedSong: Song? = null,
        val isLoadingSongs: Boolean = true,
        val currentArtistSongsList: List<Song> = emptyList(),
        val currentAlbumSongsList: List<Song> = emptyList(),
        val currentPlaylist: Playlist = Playlist(songs = emptyList()),
        val currentPlaylists: List<Playlist> = emptyList(),
        val isLoadingPlaylists: Boolean = true,
        val topBarText: String? = null,
        val previousTabIndex: Int = 1, // Default to Songs tab
        val returningFromDetail: Boolean = false,
        val pendingSaveJobs: List<Job> = emptyList(),
        val songsPathChanged: Boolean = false,
    )
}
