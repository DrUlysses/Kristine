package dr.ulysses.network

import dr.ulysses.entities.Playlist
import dr.ulysses.entities.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

internal const val BROADCAST_MESSAGE_PREFIX = "Kristine Server Discovery:"
internal const val DISCOVERY_PORT = 45678
internal val BROADCAST_INTERVAL = 5.seconds

/** The server's TLS port, unless another app holds it. */
internal const val SERVER_PORT = 45679

/** Serves the file of a song in the current window: `GET /song?path=<server path>`. */
internal const val SONG_ROUTE = "/song"

/** Serves the server's [Library], which a connected client shows instead of its own. */
internal const val LIBRARY_ROUTE = "/library"

/** Serves the stored artwork of a library song: `GET /artwork?path=<server path>`. */
internal const val ARTWORK_ROUTE = "/artwork"

/** The server's songs and playlists (with their songs), without song artwork. */
@Serializable
data class Library(val songs: List<Song>, val playlists: List<Playlist>)

/** Plays commands from LAN clients, pushes [PlayerUpdate]s to them and announces itself over UDP. */
expect class NetworkServer() {
    suspend fun start(): ServerInfo
    fun stop()
    fun sendPlayerUpdate(update: PlayerUpdate)
}

/** Emits (address, port) for every server announcement heard on the LAN. */
internal expect fun serverAnnouncements(): Flow<Pair<String, Int>>

/** Server songs downloaded to play here, kept by their server path. */
internal expect class SongCache() {
    /** The local path of the server song at [serverPath], downloading it first; null where files can't be stored. */
    suspend fun pathFor(serverPath: String, download: suspend () -> ByteArray): String?

    /** Deletes every cached song except [serverPaths]. */
    suspend fun retain(serverPaths: Set<String>)
}
