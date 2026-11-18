package dr.ulysses.network

import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

/**
 * Enum representing the type of websocket command.
 * This replaces string-based commands for better type safety.
 */
@Serializable
enum class WebSocketCommandType(val value: String, val description: String) {
    /**
     * Command to play a specific song.
     */
    PLAY("play", "Play a specific song"),

    /**
     * Command to pause the current playback.
     */
    PAUSE("pause", "Pause the current playback"),

    /**
     * Command to resume the paused playback.
     */
    RESUME("resume", "Resume the paused playback"),

    /**
     * Command to play the next song in the playlist.
     */
    NEXT("next", "Play the next song"),

    /**
     * Command to play the previous song in the playlist.
     */
    PREVIOUS("previous", "Play the previous song"),

    /**
     * Command to set the entire playlist on the server.
     */
    SET_PLAYLIST("set_playlist", "Set the entire playlist");
}

/**
 * Base interface for all websocket commands.
 */
@Serializable
sealed interface WebSocketCommand {
    val commandType: WebSocketCommandType
}

/**
 * Command to play a specific song.
 * @param song The song to play.
 */
@Serializable
data class PlaySongCommand(
    val song: Song,
) : WebSocketCommand {
    override val commandType: WebSocketCommandType = WebSocketCommandType.PLAY
}

/**
 * Simple command with no additional parameters.
 */
@Serializable
data class SimpleCommand(
    override val commandType: WebSocketCommandType,
) : WebSocketCommand

/**
 * Command to set the entire playlist on the server.
 * @param songs The list of songs in the playlist.
 * @param currentSongIndex The index of the current song in the playlist.
 */
@Serializable
data class SetPlaylistCommand(
    val songs: List<Song>,
    val currentSongIndex: Int,
) : WebSocketCommand {
    override val commandType: WebSocketCommandType = WebSocketCommandType.SET_PLAYLIST
}
