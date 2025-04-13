package dr.ulysses.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.models.RepeatMode.*
import dr.ulysses.network.NetworkManager

/**
 * Represents the repeat mode for a media player.
 *
 * The repeat mode controls how the player behaves when it reaches the end of a playlist.
 * It includes the following modes:
 *
 * - [None]: Playback stops at the end of the playlist.
 * - [All]: The playlist starts over from the beginning after finishing.
 * - [One]: The currently playing song is repeated indefinitely.
 */
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
                    None -> All
                    All -> One
                    One -> None
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
        when {
            // For RepeatMode.One, replay the current song
            state.repeatMode == One -> {
                state.currentPlaylist.songs.getOrNull(state.currentTrackNum)?.let {
                    setState { copy(currentSong = it) }
                    setCurrentTrackNumOnDevice(state.currentTrackNum)
                    resumeCurrentSongOnDevice()
                }
            }
            // If not at the end of the playlist, go to the next song
            state.currentTrackNum < state.currentPlaylist.songs.size - 1 -> {
                state.currentPlaylist.songs.getOrNull(state.currentTrackNum + 1)?.let {
                    setState { copy(currentSong = it, currentTrackNum = state.currentTrackNum + 1) }
                    playNextOnDevice()
                }
            }
            // When in RepeatMode.All and at the end of the playlist, wrap around to the first song
            state.repeatMode == All && state.currentPlaylist.songs.isNotEmpty() -> {
                if (state.shuffle) {
                    // Reshuffle the playlist when it ends with shuffle active
                    val shuffledSongs = state.currentPlaylist.songs.shuffled()
                    setState {
                        copy(
                            currentPlaylist = state.currentPlaylist.copy(songs = shuffledSongs),
                            currentSong = shuffledSongs.firstOrNull(),
                            currentTrackNum = 0
                        )
                    }
                    setPlayListOnDevice(shuffledSongs.map { it.path })
                    setCurrentTrackNumOnDevice(0)
                    resumeCurrentSongOnDevice()
                } else {
                    // Standard behavior without shuffle
                    state.currentPlaylist.songs.getOrNull(0)?.let {
                        setState { copy(currentSong = it, currentTrackNum = 0) }
                        setCurrentTrackNumOnDevice(0)
                        resumeCurrentSongOnDevice()
                    }
                }
            }
            // For RepeatMode.None, do nothing when at the end of the playlist
        }
    }

    fun onPreviousCommand() {
        when {
            // For RepeatMode.One, replay the current song
            state.repeatMode == One -> {
                state.currentPlaylist.songs.getOrNull(state.currentTrackNum)?.let {
                    setState { copy(currentSong = it) }
                    setCurrentTrackNumOnDevice(state.currentTrackNum)
                    resumeCurrentSongOnDevice()
                }
            }
            // If not at the beginning of the playlist, go to the previous song
            state.currentTrackNum > 0 -> {
                state.currentPlaylist.songs.getOrNull(state.currentTrackNum - 1)?.let {
                    setState { copy(currentSong = it, currentTrackNum = state.currentTrackNum - 1) }
                    playPreviousOnDevice()
                }
            }
            // When in RepeatMode.All and at the beginning of the playlist, wrap around to the last song
            state.repeatMode == All && state.currentPlaylist.songs.isNotEmpty() -> {
                val lastIndex = state.currentPlaylist.songs.size - 1
                state.currentPlaylist.songs.getOrNull(lastIndex)?.let {
                    setState { copy(currentSong = it, currentTrackNum = lastIndex) }
                    setCurrentTrackNumOnDevice(lastIndex)
                    resumeCurrentSongOnDevice()
                }
            }
            // For RepeatMode.None, do nothing when at the beginning of the playlist
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
        val oldState = state
        state = state.update()

        // Send updates if the state has changed
        if (oldState.currentSong != state.currentSong) {
            sendNowPlayingUpdate(state.currentSong)
        }
        if (oldState.isPlaying != state.isPlaying) {
            sendPlaybackStateUpdate(state.isPlaying)
        }
    }

    /**
     * Sends a "nowPlaying" update to connected clients.
     * @param song The currently playing song.
     */
    private fun sendNowPlayingUpdate(song: Song?) {
        if (song != null) {
            val update = kotlinx.serialization.json.Json.encodeToString(
                mapOf(
                    "type" to "nowPlaying",
                    "song" to kotlinx.serialization.json.Json.encodeToString(song)
                )
            )
            NetworkManager.sendPlayerUpdate(update)
        }
    }

    /**
     * Sends a "playbackState" update to connected clients.
     * @param isPlaying Whether playback is active.
     */
    private fun sendPlaybackStateUpdate(isPlaying: Boolean) {
        val update = kotlinx.serialization.json.Json.encodeToString(
            mapOf(
                "type" to "playbackState",
                "isPlaying" to isPlaying
            )
        )
        NetworkManager.sendPlayerUpdate(update)
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
        val repeatMode: RepeatMode = All,
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
