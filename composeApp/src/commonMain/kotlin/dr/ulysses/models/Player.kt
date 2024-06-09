package dr.ulysses.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Song

internal object PlayerService {
    var state: PlayerState by mutableStateOf(initialState())
        private set

    fun onPlaySongCommand(song: Song) {
        val currentTrackNum = state.currentTrackSequence.entries.find { it.value == song }?.key
        // If the song is already in the current track sequence, set it as the current track
        if (currentTrackNum != null && state.currentSong != null) {
            setState { copy(currentTrackNum = currentTrackNum) }
            setCurrentTrackNumOnDevice(currentTrackNum)
        }
        // If the song is not in the current track sequence, set it as the first track
        else {
            state.currentTrackSequence[0] = song
            setState {
                copy(
                    currentTrackSequence = state.currentTrackSequence,
                    currentTrackNum = 0
                )
            }
            setPlayListOnDevice(state.currentTrackSequence.values.map { it.path })
        }
        onResumeCommand()
    }

    fun onFindSongCommand(query: String): List<Song> {
        return state.currentTrackSequence.values.filter {
            it.title.contains(
                query, ignoreCase = true
            ) || it.artist.contains(query, ignoreCase = true) || it.album?.contains(
                query, ignoreCase = true
            ) == true || it.path.contains(query, ignoreCase = true)
        }
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        setState { copy(isPlaying = isPlaying) }
        if (isPlaying)
            resumeCurrentSongOnDevice()
        else
            pauseCurrentSongOnDevice()
    }

    fun onPlayCommand() {
        val song = state.currentTrackSequence[state.currentTrackNum]
        if (song != null) {
            setState { copy(currentSong = song) }
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
        if (isPlayingOnDevice()) pauseCurrentSongOnDevice() else resumeCurrentSongOnDevice()
    }

    fun onStopCommand() {
        setState { copy(currentSong = null) }
        stopCurrentSongOnDevice()
    }

    fun onSeekCommand(position: Int) {
        seekToOnDevice(position)
    }

    fun onNextCommand() {
        if (state.currentTrackNum < state.currentTrackSequence.size - 1) {
            state.currentTrackSequence[state.currentTrackNum + 1]?.let {
                setState { copy(currentSong = it, currentTrackNum = state.currentTrackNum + 1) }
                playNextOnDevice()
            }
        }
    }

    fun onPreviousCommand() {
        if (state.currentTrackNum > 0) {
            state.currentTrackSequence[state.currentTrackNum - 1]?.let {
                setState { copy(currentSong = it, currentTrackNum = state.currentTrackNum - 1) }
                playPreviousOnDevice()
            }
        }
    }

    fun onSongsChanged(songs: List<Song>) {
        setState {
            copy(
                currentTrackSequence = linkedMapOf<Int, Song>().apply {
                    songs.forEachIndexed { index, song -> put(index, song) }
                }
            )
        }
    }

    private fun initialState() = PlayerState().also {
        isPlayingChangedOnDevice { isPlaying -> setState { copy(isPlaying = isPlaying) } }
        currentPlayingChangedOnDevice { path ->
            setState {
                copy(currentSong = state.currentTrackSequence.values.find { it.path == path })
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
     * @param currentTrackSequence Current track sequence, with track number as key and path as value
     * @param currentTrackNum Current track number
     * @param isRemotePlaying Is the player playing remotely
     */
    data class PlayerState(
        val currentSong: Song? = null,
        val isPlaying: Boolean = false,
        val currentTrackSequence: LinkedHashMap<Int, Song> = linkedMapOf(),
        val currentTrackNum: Int = 0,
        val isRemotePlaying: Boolean = false,
        val onPlayingChangedOnDevice: (Boolean) -> Unit = {},
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
