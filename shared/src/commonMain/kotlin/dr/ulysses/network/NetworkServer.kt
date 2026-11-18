package dr.ulysses.network

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.seconds

internal const val BROADCAST_MESSAGE_PREFIX = "Kristine Server Discovery:"
internal const val DISCOVERY_PORT = 45678
internal val BROADCAST_INTERVAL = 5.seconds

/** Plays commands from LAN clients, pushes [PlayerUpdate]s to them and announces itself over UDP. */
expect class NetworkServer() {
    suspend fun start(): ServerInfo
    fun stop()
    fun sendPlayerUpdate(update: PlayerUpdate)
}

/** Emits (address, port) for every server announcement heard on the LAN. */
internal expect fun serverAnnouncements(): Flow<Pair<String, Int>>
