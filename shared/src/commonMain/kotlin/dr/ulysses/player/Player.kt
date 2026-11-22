package dr.ulysses.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.network.*
import dr.ulysses.ui.views.getCurrentPosition
import dr.ulysses.ui.views.getVolume
import dr.ulysses.ui.views.setVolume
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

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

    // The server song this device plays along with, as a local file path
    private var followedPath: String? = null

    private val remote get() = state.isRemotePlaying

    /**
     * As a remote control: the server's volume, 0 to 1.
     */
    var remoteVolume by mutableStateOf(1f)
        private set

    // As a remote control: the server's position and when it came, counted on while the server plays
    private var remotePosition = 0
    private var remotePositionAt = TimeSource.Monotonic.markNow()

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

    /**
     * The current song's position in seconds, on this device or on the connected server.
     */
    fun position(): Int = if (remote) {
        remotePosition + if (state.isPlaying) {
            remotePositionAt.elapsedNow().inWholeSeconds.toInt()
        } else {
            0
        }
    } else {
        getCurrentPosition()
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
            // The device played the server's songs; it gets the local queue back, paused
            if (followedPath != null) {
                followedPath = null
                stopCurrentSongOnDevice()
                setPlayListOnDevice(state.currentPlaylist.songs)
            }
        }
    }

    /** As a remote control: shows the server's queue window. */
    fun onRemoteWindow(window: QueueWindowUpdate) {
        if (!remote) {
            return
        }
        window.volume?.let { remoteVolume = it }
        remotePosition = window.position ?: 0
        remotePositionAt = TimeSource.Monotonic.markNow()
        setState {
            copy(
                currentSong = window.current,
                isPlaying = window.isPlaying,
                currentPlaylist = Playlist(
                    name = "Server",
                    songs = window.queue ?: listOfNotNull(window.previous, window.current, window.next)
                ),
                currentTrackNum = when {
                    window.queue != null -> window.queue.indexOfFirst { it.path == window.current?.path }
                        .coerceAtLeast(0)

                    window.previous != null -> 1
                    else -> 0
                }
            )
        }
    }

    /** As a remote control that plays along: plays [song] (a local copy of the server's current song) on this device. */
    fun followOnDevice(
        song: Song?,
        isPlaying: Boolean,
    ) {
        if (!remote) {
            return
        }
        if (song?.path != followedPath) {
            followedPath = song?.path
            if (song == null) {
                stopCurrentSongOnDevice()
                return
            }
            setPlayListOnDevice(listOf(song))
            playTrackOnDevice(0)
        }
        if (song == null) {
            return
        }
        if (isPlaying) {
            resumeCurrentSongOnDevice()
        } else {
            pauseCurrentSongOnDevice()
        }
    }

    fun onPlaySongCommand(song: Song) {
        if (!song.isPlayable()) {
            return
        }
        if (remote) {
            // A pick from the server's window ("Up next") moves the server's queue instead of replacing it
            when (song) {
                state.currentPlaylist.songs.getOrNull(state.currentTrackNum + 1) -> {
                    return NetworkManager.send(NextCommand)
                }

                state.currentPlaylist.songs.getOrNull(state.currentTrackNum - 1) -> {
                    return NetworkManager.send(PreviousCommand)
                }
            }
            // A song of the server's queue: the server jumps to it; any other plays on its own
            if (state.currentPlaylist.songs.any { it.path == song.path }) {
                return NetworkManager.send(PlaySongCommand(song.copy(artwork = null)))
            }
            startQueue(
                songs = listOf(song),
                first = song
            )
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
        playTrack(trackNum)
    }

    /** The device got a queue from outside the app (Android Auto, Bluetooth) and is starting it at [trackNum]. */
    fun onQueueSetByDevice(
        playlist: Playlist,
        trackNum: Int,
    ) {
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
            NetworkManager.send(ResumeCommand)
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

    /**
     * Sets this device's volume, or the connected server's.
     */
    fun onVolumeCommand(volume: Float) {
        if (remote) {
            remoteVolume = volume
            return NetworkManager.send(SetVolumeCommand(volume))
        }
        setVolume(volume)
        // Other clients show the new volume too
        NetworkManager.sendPlayerUpdate(
            state.window().copy(
                position = getCurrentPosition(),
                volume = volume
            )
        )
    }

    fun onSeekCommand(position: Int) {
        if (remote) {
            remotePosition = position
            remotePositionAt = TimeSource.Monotonic.markNow()
            return NetworkManager.send(SeekCommand(position))
        }
        seekToOnDevice(position)
        // Clients count on from the position they were told, which a seek changes
        NetworkManager.sendPlayerUpdate(
            state.window().copy(
                position = position,
                volume = getVolume()
            )
        )
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

    /**
     * Starts a new queue of [songs] at [first], as a click in a song list or a playlist does; moving within the
     * queue (next, previous, a click in the queue) is [onPlaySongCommand].
     */
    fun startQueue(
        songs: List<Song>,
        first: Song,
    ) {
        if (!remote) {
            onSongsChanged(songs)
            onPlaySongCommand(first)
            return
        }
        // The server answers with its new window; the songs go without artwork to keep the message small
        val queue = songs.filter { it.isPlayable() }
        NetworkManager.send(
            SetPlaylistCommand(
                songs = queue.map {
                    it.copy(
                        artwork = null
                    )
                },
                currentSongIndex = queue.indexOf(first)
            )
        )
    }

    fun onSongsChanged(songs: List<Song>) {
        val playable = songs.filter { it.isPlayable() }
        if (remote) {
            return
        }
        setState {
            copy(
                currentPlaylist = currentPlaylist.copy(
                    songs = playable
                )
            )
        }
        setPlayListOnDevice(playable)
    }

    /** Takes [song] out of the local queue; the current song stops, any other keeps playing. */
    fun onRemoveFromQueueCommand(song: Song) {
        if (remote) {
            return
        }
        val songs = state.currentPlaylist.songs
        val index = songs.indexOfFirst { it.path == song.path }
        if (index == -1) {
            return
        }
        val isCurrent = index == state.currentTrackNum
        if (isCurrent) {
            stopCurrentSongOnDevice()
        }
        removeFromPlayListOnDevice(index)
        setState {
            copy(
                currentPlaylist = currentPlaylist.copy(
                    songs = songs.filterIndexed { i, _ -> i != index }
                ),
                // Play resumes where the removed song was, so the next song plays next
                currentTrackNum = if (index < currentTrackNum) {
                    currentTrackNum - 1
                } else {
                    currentTrackNum
                },
                currentSong = if (isCurrent) {
                    null
                } else {
                    currentSong
                }
            )
        }
    }

    // A missing file must never reach the device player: VLC errors on it and the skip chain can crash it
    private fun Song.isPlayable() = state != Song.State.NotDownloaded

    private fun PlayerState.window(): QueueWindowUpdate {
        val songs = currentPlaylist.songs

        // Songs go without artwork: it would make every message megabytes big
        fun at(index: Int) = songs.getOrNull(index)?.copy(
            artwork = null
        )
        return QueueWindowUpdate(
            current = currentSong?.copy(
                artwork = null
            ),
            next = at(currentTrackNum + 1),
            previous = at(currentTrackNum - 1),
            isPlaying = isPlaying
        )
    }

    private inline fun setState(update: PlayerState.() -> PlayerState) {
        val old = state
        state = state.update()
        // Only the device that really plays reports to its clients
        if (remote) {
            return
        }
        val window = state.window()
        if (window != old.window()) {
            NetworkManager.sendPlayerUpdate(
                window.copy(
                    position = getCurrentPosition(),
                    volume = getVolume()
                )
            )
        }
    }
}

expect fun setPlayListOnDevice(songs: List<Song>)

/** Removes the queue entry at [index] without interrupting the song that plays. */
expect fun removeFromPlayListOnDevice(index: Int)

/** Starts playing the queue at [trackNum] in one step; a separate resume could race the switch and play another track. */
expect fun playTrackOnDevice(trackNum: Int)
expect fun pauseCurrentSongOnDevice()
expect fun isPlayingOnDevice(): Boolean
expect fun resumeCurrentSongOnDevice()
expect fun stopCurrentSongOnDevice()
expect fun seekToOnDevice(position: Int)
expect fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit)
expect fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit)
