package dr.ulysses.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// A browser can't open a server socket or use UDP, so the web app is a client only
actual class NetworkServer {
    actual suspend fun start(): ServerInfo = ServerInfo(0)
    actual fun stop() {}
    actual fun sendPlayerUpdate(update: PlayerUpdate) {}
}

internal actual fun serverAnnouncements(): Flow<Pair<String, Int>> = emptyFlow()
