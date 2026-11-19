package dr.ulysses.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.network.*
import kotlin.concurrent.Volatile

enum class RepeatMode {
    None,
    All,
    One,
}

/**
 * @param isRemotePlaying Commands go to the connected server instead of this device
 */
data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentTrackNum: Int = 0,
    val currentPlaylist: Playlist = Playlist(),
    val isRemotePlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.All,
)

object Player {
    var state: PlayerState by mutableStateOf(PlayerState())
        private set

    // The local queue survives a session as a remote control
    private var localState: PlayerState? = null

    private val remote get() = state.isRemotePlaying

    // The track the app last asked the device for. Device reports lag behind fast clicking, so until the device
    // confirms this track, reports about other tracks are stale and must not move the UI back
    @Volatile
    private var requestedTrack: Int? = null

    init {
        isPlayingChangedOnDevice { isPlaying ->
            if (!remote) {
                setState {
                    copy(
                        isPlaying = isPlaying
                    )
                }
            }
        }
        currentPlayingChangedOnDevice { path ->
            // The device moved on by itself (end of song, skip): follow it; an unknown path changes nothing
            val index = state.currentPlaylist.songs.indexOfFirst { it.path == path }
            if (remote || index == -1) {
                return@currentPlayingChangedOnDevice
            }
            requestedTrack?.let { requested ->
                if (requested != index) {
                    return@currentPlayingChangedOnDevice
                }
                requestedTrack = null
            }
            setState {
                copy(
                    currentSong = currentPlaylist.songs[index],
                    currentTrackNum = index
                )
            }
        }
    }

    private fun playTrack(trackNum: Int) {
        requestedTrack = trackNum
        playTrackOnDevice(trackNum)
    }

    fun setRemote(remote: Boolean) {
        if (remote == this.remote) {
            return
        }
        if (remote) {
            localState = state
            state = PlayerState(
                isRemotePlaying = true,
                shuffle = state.shuffle,
                repeatMode = state.repeatMode
            )
        } else {
            state = localState ?: PlayerState()
            localState = null
        }
    }

    fun onPlaySongCommand(song: Song) {
        if (!song.isPlayable()) {
            return
        }
        // A song outside the queue goes to its front, so the device plays what the UI shows
        if (song !in state.currentPlaylist.songs) {
            onSongsChanged(listOf(song) + state.currentPlaylist.songs)
        }
        val trackNum = state.currentPlaylist.songs.indexOf(song)
        setState {
            copy(
                currentTrackNum = trackNum,
                currentSong = song
            )
        }
        if (remote) {
            NetworkManager.send(
                SetPlaylistCommand(
                    songs = state.currentPlaylist.songs,
                    currentSongIndex = trackNum
                )
            )
        } else {
            playTrack(trackNum)
        }
    }

    fun onUpdateSongCommand(song: Song) {
        setState {
            copy(
                currentSong = song,
                currentTrackNum = currentPlaylist.songs.indexOf(song)
            )
        }
    }

    /** The device got a queue from outside the app (Android Auto, Bluetooth) and is starting it at [trackNum]. */
    fun onQueueSetByDevice(playlist: Playlist, trackNum: Int) {
        if (remote) {
            return
        }
        requestedTrack = trackNum
        setState {
            copy(
                currentPlaylist = playlist,
                currentTrackNum = trackNum,
                currentSong = playlist.songs.getOrNull(trackNum)
            )
        }
    }

    fun onFindSongCommand(query: String): List<Song> = state.currentPlaylist.songs.filter {
        it.title.contains(
            other = query,
            ignoreCase = true
        ) ||
                it.artist.contains(
                    other = query,
                    ignoreCase = true
                ) ||
                it.album?.contains(
                    other = query,
                    ignoreCase = true
                ) == true ||
                it.path.contains(
                    other = query,
                    ignoreCase = true
                )
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        setState {
            copy(
                isPlaying = isPlaying
            )
        }
        if (isPlaying) {
            onResumeCommand()
        } else {
            onPauseCommand()
        }
    }

    fun onPlayCommand() {
        if (remote) {
            state.currentSong?.let { NetworkManager.send(PlaySongCommand(it)) }
            return
        }
        state.currentPlaylist.songs.getOrNull(state.currentTrackNum)?.let {
            setState {
                copy(
                    currentSong = it
                )
            }
            resumeCurrentSongOnDevice()
        }
    }

    fun onPauseCommand() = if (remote) {
        NetworkManager.send(PauseCommand)
    } else {
        pauseCurrentSongOnDevice()
    }

    fun onResumeCommand() = if (remote) {
        NetworkManager.send(ResumeCommand)
    } else {
        resumeCurrentSongOnDevice()
    }

    fun onPlayOrPauseCommand() {
        val playing = if (remote) {
            state.isPlaying
        } else {
            isPlayingOnDevice()
        }
        if (playing) {
            onPauseCommand()
        } else {
            onResumeCommand()
        }
    }

    fun onUpdatePlaybackStateCommand(isPlaying: Boolean) {
        setState {
            copy(
                isPlaying = isPlaying
            )
        }
    }

    fun onToggleShuffleCommand() {
        setState {
            copy(
                shuffle = !shuffle
            )
        }
    }

    fun setShuffle(shuffle: Boolean) {
        setState {
            copy(
                shuffle = shuffle
            )
        }
    }

    fun onSwitchRepeatCommand() {
        setState {
            copy(
                repeatMode = when (repeatMode) {
                    RepeatMode.None -> RepeatMode.All
                    RepeatMode.All -> RepeatMode.One
                    RepeatMode.One -> RepeatMode.None
                }
            )
        }
    }

    fun onStopCommand() {
        setState {
            copy(
                currentSong = null
            )
        }
        if (!remote) {
            stopCurrentSongOnDevice()
        }
    }

    fun onSeekCommand(position: Int) {
        if (!remote) {
            seekToOnDevice(position)
        }
    }

    fun onNextCommand() {
        if (remote) {
            return NetworkManager.send(NextCommand)
        }
        val songs = state.currentPlaylist.songs
        when {
            state.repeatMode == RepeatMode.One -> replayCurrent()
            state.currentTrackNum < songs.size - 1 -> {
                setState {
                    copy(
                        currentSong = songs[currentTrackNum + 1],
                        currentTrackNum = currentTrackNum + 1
                    )
                }
                playTrack(state.currentTrackNum)
            }

            state.repeatMode == RepeatMode.All && songs.isNotEmpty() -> {
                // A finished shuffled queue is reshuffled for the next round
                val queue = if (state.shuffle) {
                    songs.shuffled()
                } else {
                    songs
                }
                setState {
                    copy(
                        currentPlaylist = currentPlaylist.copy(
                            songs = queue
                        ),
                        currentSong = queue.first(),
                        currentTrackNum = 0
                    )
                }
                if (state.shuffle) {
                    setPlayListOnDevice(queue)
                }
                playTrack(0)
            }
        }
    }

    fun onPreviousCommand() {
        if (remote) {
            return NetworkManager.send(PreviousCommand)
        }
        val songs = state.currentPlaylist.songs
        when {
            state.repeatMode == RepeatMode.One -> replayCurrent()
            state.currentTrackNum > 0 -> {
                setState {
                    copy(
                        currentSong = songs[currentTrackNum - 1],
                        currentTrackNum = currentTrackNum - 1
                    )
                }
                playTrack(state.currentTrackNum)
            }

            state.repeatMode == RepeatMode.All && songs.isNotEmpty() -> {
                setState {
                    copy(
                        currentSong = songs.last(),
                        currentTrackNum = songs.lastIndex
                    )
                }
                playTrack(songs.lastIndex)
            }
        }
    }

    private fun replayCurrent() {
        val song = state.currentPlaylist.songs.getOrNull(state.currentTrackNum) ?: return
        setState {
            copy(
                currentSong = song
            )
        }
        playTrack(state.currentTrackNum)
    }

    fun onPlaylistChanged(playlist: Playlist) {
        val songs = playlist.songs.filter { it.isPlayable() }
        setState {
            copy(
                currentPlaylist = playlist.copy(
                    songs = songs
                ),
                currentTrackNum = 0
            )
        }
        if (!remote) {
            setPlayListOnDevice(songs)
        }
    }

    fun onSongsChanged(songs: List<Song>) {
        val playable = songs.filter { it.isPlayable() }
        setState {
            copy(
                currentPlaylist = currentPlaylist.copy(
                    songs = playable
                )
            )
        }
        if (!remote) {
            setPlayListOnDevice(playable)
        }
    }

    // A missing file must never reach the device player: VLC errors on it and the skip chain can crash it
    private fun Song.isPlayable() = state != Song.State.NotDownloaded

    private inline fun setState(update: PlayerState.() -> PlayerState) {
        val old = state
        state = state.update()
        // Only the device that really plays reports to its clients
        if (remote) {
            return
        }
        if (old.currentSong != state.currentSong) {
            state.currentSong?.let {
                NetworkManager.sendPlayerUpdate(
                    NowPlayingUpdate(it)
                )
            }
        }
        if (old.isPlaying != state.isPlaying) {
            NetworkManager.sendPlayerUpdate(PlaybackStateUpdate(state.isPlaying))
        }
    }
}

expect fun setPlayListOnDevice(songs: List<Song>)

/** Starts playing the queue at [trackNum] in one step; a separate resume could race the switch and play another track. */
expect fun playTrackOnDevice(trackNum: Int)
expect fun pauseCurrentSongOnDevice()
expect fun isPlayingOnDevice(): Boolean
expect fun resumeCurrentSongOnDevice()
expect fun stopCurrentSongOnDevice()
expect fun seekToOnDevice(position: Int)
expect fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit)
expect fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit)
