package dr.ulysses.network

import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

/**
 * Enum representing the type of player update.
 */
enum class PlayerUpdateType {
    NOW_PLAYING,
    PLAYBACK_STATE
}

/**
 * Base interface for all player updates.
 */
@Serializable
sealed interface PlayerUpdate {
    val type: PlayerUpdateType
}

/**
 * Update sent when the currently playing song changes.
 * @param song The currently playing song.
 */
@Serializable
data class NowPlayingUpdate(
    val song: Song,
) : PlayerUpdate {
    override val type: PlayerUpdateType = PlayerUpdateType.NOW_PLAYING
}

/**
 * Update sent when the playback state changes.
 * @param isPlaying Whether playback is active.
 */
@Serializable
data class PlaybackStateUpdate(
    val isPlaying: Boolean,
) : PlayerUpdate {
    override val type: PlayerUpdateType = PlayerUpdateType.PLAYBACK_STATE
}
