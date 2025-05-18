package dr.ulysses.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.player.RepeatMode.*

/**
 * Implementation of PlayerService for local playback.
 */
internal class LocalPlayer : PlayerService {
    override var state: PlayerState by mutableStateOf(initialState())

    override fun onPlaySongCommand(song: Song) {
        val currentTrackNum = state.currentPlaylist.songs.indexOf(song)
        // If the song is already in the current track sequence, set it as the current track
        if (currentTrackNum != -1) {
            setState { copy(currentTrackNum = currentTrackNum, currentSong = song) }
            if (state.currentSong == null)
                setPlayListOnDevice(state.currentPlaylist.songs.map { it.path })
            setCurrentTrackNumOnDevice(currentTrackNum)
        }
        // If the song is not in the current track sequence, add it to the playlist and set it as the current track
        else {
            val updatedSongs = state.currentPlaylist.songs + song
            setState {
                copy(
                    currentPlaylist = state.currentPlaylist.copy(songs = updatedSongs),
                    currentTrackNum = updatedSongs.size - 1,
                    currentSong = song
                )
            }
            setPlayListOnDevice(updatedSongs.map { it.path })
            setCurrentTrackNumOnDevice(updatedSongs.size - 1)
        }
        onResumeCommand()
    }

    override fun onUpdateSongCommand(song: Song) {
        setState {
            copy(
                currentSong = song,
                currentTrackNum = state.currentPlaylist.songs.indexOf(song)
            )
        }
    }

    override fun onFindSongCommand(query: String): List<Song> = state.currentPlaylist.songs.filter {
        it.title.contains(
            query, ignoreCase = true
        ) || it.artist.contains(query, ignoreCase = true) || it.album?.contains(
            query, ignoreCase = true
        ) == true || it.path.contains(query, ignoreCase = true)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        setState { copy(isPlaying = isPlaying) }
        if (isPlaying)
            resumeCurrentSongOnDevice()
        else
            pauseCurrentSongOnDevice()
    }

    override fun onPlayCommand() {
        state.currentPlaylist.songs.getOrNull(state.currentTrackNum)?.let {
            setState { copy(currentSong = it) }
            onResumeCommand()
        }
    }

    override fun onPauseCommand() {
        pauseCurrentSongOnDevice()
    }

    override fun onResumeCommand() {
        resumeCurrentSongOnDevice()
    }

    override fun onPlayOrPauseCommand() {
        if (isPlayingOnDevice()) onPauseCommand() else onResumeCommand()
    }

    override fun onUpdatePlaybackStateCommand(isPlaying: Boolean) {
        setState { copy(isPlaying = isPlaying) }
    }

    override fun onToggleShuffleCommand() {
        setState { copy(shuffle = !state.shuffle) }
    }

    override fun setShuffle(shuffle: Boolean) {
        setState { copy(shuffle = shuffle) }
    }

    override fun onSwitchRepeatCommand() {
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

    override fun onStopCommand() {
        setState { copy(currentSong = null) }
        stopCurrentSongOnDevice()
    }

    override fun onSeekCommand(position: Int) {
        seekToOnDevice(position)
    }

    override fun onNextCommand() {
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

    override fun onPreviousCommand() {
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

    override fun onPlaylistChanged(playlist: Playlist) {
        setState {
            copy(
                currentPlaylist = playlist,
                currentTrackNum = 0,
            )
        }
        setPlayListOnDevice(playlist.songs.map { it.path })
    }

    override fun onSongsChanged(songs: List<Song>) {
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
            Player.sendNowPlayingUpdate(state.currentSong)
        }
        if (oldState.isPlaying != state.isPlaying) {
            Player.sendPlaybackStateUpdate(state.isPlaying)
        }
    }
}
