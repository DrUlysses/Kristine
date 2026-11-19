package dr.ulysses.network

import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable

/** Server → client messages. */
@Serializable
sealed interface PlayerUpdate

/** The server's queue around its current song, in the order current, next, previous, and whether it plays. */
@Serializable
data class QueueWindowUpdate(
    val current: Song?,
    val next: Song?,
    val previous: Song?,
    val isPlaying: Boolean,
) : PlayerUpdate {
    val songs get() = listOfNotNull(current, next, previous)
}
