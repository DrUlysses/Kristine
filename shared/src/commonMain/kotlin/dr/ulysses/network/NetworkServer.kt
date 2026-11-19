package dr.ulysses.network

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.seconds

internal const val BROADCAST_MESSAGE_PREFIX = "Kristine Server Discovery:"
internal const val DISCOVERY_PORT = 45678
internal val BROADCAST_INTERVAL = 5.seconds

/** Serves the file of a song in the current window: `GET /song?path=<server path>`. */
internal const val SONG_ROUTE = "/song"

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
