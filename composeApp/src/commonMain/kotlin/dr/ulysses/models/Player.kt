package dr.ulysses.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song

enum class RepeatMode {
    None,
    All,
    One,
}

internal object PlayerService {
    var state: PlayerState by mutableStateOf(initialState())
        private set

    fun onPlaySongCommand(song: Song) {
        val currentTrackNum = state.currentPlaylist.songs.indexOf(song)
        // If the song is already in the current track sequence, set it as the current track
        if (currentTrackNum != -1) {
            setState { copy(currentTrackNum = currentTrackNum, currentSong = song) }
            if (state.currentSong == null)
                setPlayListOnDevice(state.currentPlaylist.songs.map { it.path })
            setCurrentTrackNumOnDevice(currentTrackNum)
        }
        // If the song is not in the current track sequence, set it as the first track
        else {
            setState {
                copy(
                    currentTrackNum = 0,
                    currentSong = song
                )
            }
            setPlayListOnDevice(state.currentPlaylist.songs.map { it.path })
            setCurrentTrackNumOnDevice(0)
        }
        onResumeCommand()
    }

    fun onFindSongCommand(query: String): List<Song> = state.currentPlaylist.songs.filter {
        it.title.contains(
            query, ignoreCase = true
        ) || it.artist.contains(query, ignoreCase = true) || it.album?.contains(
            query, ignoreCase = true
        ) == true || it.path.contains(query, ignoreCase = true)
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        setState { copy(isPlaying = isPlaying) }
        if (isPlaying)
            resumeCurrentSongOnDevice()
        else
            pauseCurrentSongOnDevice()
    }

    fun onPlayCommand() {
        state.currentPlaylist.songs.getOrNull(state.currentTrackNum)?.let {
            setState { copy(currentSong = it) }
            onResumeCommand()
        }
    }

    fun onPauseCommand() {
        pauseCurrentSongOnDevice()
    }

    fun onResumeCommand() {
        resumeCurrentSongOnDevice()
    }

    fun onPlayOrPauseCommand() {
        if (isPlayingOnDevice()) onPauseCommand() else onResumeCommand()
    }

    fun onToggleShuffleCommand() {
        setState { copy(shuffle = !state.shuffle) }
    }

    fun setShuffle(shuffle: Boolean) {
        setState { copy(shuffle = shuffle) }
    }

    fun onSwitchRepeatCommand() {
        setState {
            copy(
                repeatMode = when (state.repeatMode) {
                    RepeatMode.None -> RepeatMode.All
                    RepeatMode.All -> RepeatMode.One
                    RepeatMode.One -> RepeatMode.None
                }
            )
        }
    }

    fun onStopCommand() {
        setState { copy(currentSong = null) }
        stopCurrentSongOnDevice()
    }

    fun onSeekCommand(position: Int) {
        seekToOnDevice(position)
    }

    fun onNextCommand() {
        if (state.currentTrackNum < state.currentPlaylist.songs.size - 1) {
            state.currentPlaylist.songs.getOrNull(state.currentTrackNum + 1)?.let {
                setState { copy(currentSong = it, currentTrackNum = state.currentTrackNum + 1) }
                playNextOnDevice()
            }
        }
    }

    fun onPreviousCommand() {
        if (state.currentTrackNum > 0) {
            state.currentPlaylist.songs.getOrNull(state.currentTrackNum - 1)?.let {
                setState { copy(currentSong = it, currentTrackNum = state.currentTrackNum - 1) }
                playPreviousOnDevice()
            }
        }
    }

    fun onPlaylistChanged(playlist: Playlist) {
        setState {
            copy(
                currentPlaylist = playlist,
                currentTrackNum = 0,
            )
        }
        setPlayListOnDevice(playlist.songs.map { it.path })
    }

    fun onSongsChanged(songs: List<Song>) {
        setState {
            copy(
                currentPlaylist = state.currentPlaylist.copy(songs = songs),
            )
        }
        setPlayListOnDevice(songs.map { it.path })
    }

    private fun initialState() = PlayerState().also {
        isPlayingChangedOnDevice { isPlaying -> setState { copy(isPlaying = isPlaying) } }
        currentPlayingChangedOnDevice { path ->
            setState {
                copy(currentSong = state.currentPlaylist.songs.find { it.path == path })
            }
        }
    }

    private inline fun setState(update: PlayerState.() -> PlayerState) {
        state = state.update()
    }

    /**
     * State of the player.
     *
     * @param currentSong Current song playing
     * @param isPlaying Is the player playing on device/remote
     * @param currentPlaylist Current playlist, with track number as key and path as value
     * @param currentTrackNum Current track number
     * @param isRemotePlaying Is the player playing remotely
     */
    data class PlayerState(
        val currentSong: Song? = null,
        val isPlaying: Boolean = false,
        val currentTrackNum: Int = 0,
        val currentPlaylist: Playlist = Playlist(),
        val isRemotePlaying: Boolean = false,
        val onPlayingChangedOnDevice: (Boolean) -> Unit = {},
        val shuffle: Boolean = false,
        val repeatMode: RepeatMode = RepeatMode.All,
    )
}

expect fun setPlayListOnDevice(paths: List<String>)

expect fun setCurrentTrackNumOnDevice(trackNum: Int)

expect fun pauseCurrentSongOnDevice()

expect fun playNextOnDevice()

expect fun playPreviousOnDevice()

expect fun isPlayingOnDevice(): Boolean

expect fun resumeCurrentSongOnDevice()

expect fun stopCurrentSongOnDevice()

expect fun seekToOnDevice(position: Int)

expect fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit)

expect fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit)
