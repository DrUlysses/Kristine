package dr.ulysses.network

import dr.ulysses.entities.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Server → client messages. */
@Serializable
sealed interface PlayerUpdate

/**
 * The server's queue around its current song, in the order current, next, previous, and whether it plays; [queue] is
 * the whole queue where the server shows it (MPD), null where it sends only this window (Kristine). [position] is
 * the current song's position in seconds when the update was sent, [volume] the server's volume from 0 to 1 (null
 * where it has none to set).
 */
@Serializable
data class QueueWindowUpdate(
    val current: Song?,
    val next: Song?,
    val previous: Song?,
    val isPlaying: Boolean,
    val queue: List<Song>? = null,
    val position: Int? = null,
    val volume: Float? = null,
) : PlayerUpdate {
    @Transient
    val songs = listOfNotNull(current, next, previous)
}
