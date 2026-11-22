package dr.ulysses.network

import dr.ulysses.entities.Song
import dr.ulysses.player.RepeatMode
import kotlinx.serialization.Serializable

/** Client → server messages. The serializer tags each message with its class, so no type field is needed. */
@Serializable
sealed interface WebSocketCommand

@Serializable
data class PlaySongCommand(val song: Song) : WebSocketCommand

/** Replaces the server's queue and plays the song at [currentSongIndex]. */
@Serializable
data class SetPlaylistCommand(
    val songs: List<Song>,
    val currentSongIndex: Int,
) : WebSocketCommand

@Serializable
data object PauseCommand : WebSocketCommand

/** Sets the volume, 0 to 1. */
@Serializable
data class SetVolumeCommand(val volume: Float) : WebSocketCommand

/** Moves the current song to [position] seconds. */
@Serializable
data class SeekCommand(val position: Int) : WebSocketCommand

/** Shuffles the songs after the current one, or brings back their order. */
@Serializable
data class ShuffleCommand(val shuffle: Boolean) : WebSocketCommand

@Serializable
data class RepeatCommand(val mode: RepeatMode) : WebSocketCommand

@Serializable
data object ResumeCommand : WebSocketCommand

@Serializable
data object NextCommand : WebSocketCommand

@Serializable
data object PreviousCommand : WebSocketCommand
