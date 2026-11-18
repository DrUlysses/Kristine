package dr.ulysses.network

import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

/** Server → client messages. */
@Serializable
sealed interface PlayerUpdate

@Serializable
data class NowPlayingUpdate(val song: Song) : PlayerUpdate

@Serializable
data class PlaybackStateUpdate(val isPlaying: Boolean) : PlayerUpdate
