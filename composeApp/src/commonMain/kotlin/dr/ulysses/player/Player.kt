package dr.ulysses.player

import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import dr.ulysses.network.NetworkManager
import dr.ulysses.player.RepeatMode.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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

/**
 * Interface representing any player service.
 * This can be implemented by different player types (local or network).
 */
interface PlayerService {
    var state: PlayerState

    fun onPlaySongCommand(song: Song)
    fun onFindSongCommand(query: String): List<Song>
    fun onIsPlayingChanged(isPlaying: Boolean)
    fun onPlayCommand()
    fun onPauseCommand()
    fun onResumeCommand()
    fun onPlayOrPauseCommand()
    fun onToggleShuffleCommand()
    fun setShuffle(shuffle: Boolean)
    fun onSwitchRepeatCommand()
    fun onStopCommand()
    fun onSeekCommand(position: Int)
    fun onNextCommand()
    fun onPreviousCommand()
    fun onPlaylistChanged(playlist: Playlist)
    fun onSongsChanged(songs: List<Song>)
}

/**
 * Static player object that holds the current player implementation.
 * This allows switching between local and network player implementations.
 */
object Player {
    private var currentPlayer: PlayerService = LocalPlayer()

    val state: PlayerState
        get() = currentPlayer.state

    fun setLocalPlayer() {
        currentPlayer = LocalPlayer()
    }

    fun setNetworkPlayer() {
        currentPlayer = NetworkPlayer()
    }

    fun onPlaySongCommand(song: Song) = currentPlayer.onPlaySongCommand(song)
    fun onFindSongCommand(query: String): List<Song> = currentPlayer.onFindSongCommand(query)
    fun onIsPlayingChanged(isPlaying: Boolean) = currentPlayer.onIsPlayingChanged(isPlaying)
    fun onPlayCommand() = currentPlayer.onPlayCommand()
    fun onPauseCommand() = currentPlayer.onPauseCommand()
    fun onResumeCommand() = currentPlayer.onResumeCommand()
    fun onPlayOrPauseCommand() = currentPlayer.onPlayOrPauseCommand()
    fun onToggleShuffleCommand() = currentPlayer.onToggleShuffleCommand()
    fun setShuffle(shuffle: Boolean) = currentPlayer.setShuffle(shuffle)
    fun onSwitchRepeatCommand() = currentPlayer.onSwitchRepeatCommand()
    fun onStopCommand() = currentPlayer.onStopCommand()
    fun onSeekCommand(position: Int) = currentPlayer.onSeekCommand(position)
    fun onNextCommand() = currentPlayer.onNextCommand()
    fun onPreviousCommand() = currentPlayer.onPreviousCommand()
    fun onPlaylistChanged(playlist: Playlist) = currentPlayer.onPlaylistChanged(playlist)
    fun onSongsChanged(songs: List<Song>) = currentPlayer.onSongsChanged(songs)

    /**
     * Sends a "nowPlaying" update to connected clients.
     * @param song The currently playing song.
     */
    internal fun sendNowPlayingUpdate(song: Song?) {
        if (song != null) {
            val update = kotlinx.serialization.json.Json.encodeToString(
                buildJsonObject {
                    put("type", JsonPrimitive("nowPlaying"))
                    put("song", JsonPrimitive(kotlinx.serialization.json.Json.encodeToString(song)))
                }
            )
            NetworkManager.sendPlayerUpdate(update)
        }
    }

    /**
     * Sends a "playbackState" update to connected clients.
     * @param isPlaying Whether playback is active.
     */
    internal fun sendPlaybackStateUpdate(isPlaying: Boolean) {
        val update = kotlinx.serialization.json.Json.encodeToString(
            buildJsonObject {
                put("type", JsonPrimitive("playbackState"))
                put("isPlaying", JsonPrimitive(isPlaying))
            }
        )
        NetworkManager.sendPlayerUpdate(update)
    }
}

// Platform-specific function declarations
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
