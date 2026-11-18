package dr.ulysses.network

import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

/** Client → server messages. The serializer tags each message with its class, so no type field is needed. */
@Serializable
sealed interface WebSocketCommand

@Serializable
data class PlaySongCommand(val song: Song) : WebSocketCommand

/** Replaces the server's queue and plays the song at [currentSongIndex]. */
@Serializable
data class SetPlaylistCommand(val songs: List<Song>, val currentSongIndex: Int) : WebSocketCommand

@Serializable
data object PauseCommand : WebSocketCommand

@Serializable
data object ResumeCommand : WebSocketCommand

@Serializable
data object NextCommand : WebSocketCommand

@Serializable
data object PreviousCommand : WebSocketCommand
