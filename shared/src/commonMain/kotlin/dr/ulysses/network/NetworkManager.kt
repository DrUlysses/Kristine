package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import dr.ulysses.player.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerInfo(
    val port: Int,
    val addresses: List<String> = emptyList(),
)

/** Owns this device's server, and its client connection to another device. */
object NetworkManager {
    private val server = NetworkServer()
    private val client = NetworkClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _localServer = MutableStateFlow(ServerInfo(0))
    val localServer: StateFlow<ServerInfo> = _localServer.asStateFlow()

    /** Address → port. */
    private val _discoveredServers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val discoveredServers: StateFlow<Map<String, Int>> = _discoveredServers.asStateFlow()

    private val _currentServer = MutableStateFlow<Pair<String, Int>?>(null)
    val currentServer: StateFlow<Pair<String, Int>?> = _currentServer.asStateFlow()

    private val _isConnected = MutableStateFlow(false)

    fun start() {
        scope.launch {
            try {
                _localServer.value = server.start()
            } catch (e: Exception) {
                ensureActive()
                // The app still works as a client and as a local player
                Logger.e(e) { "Local server failed to start" }
            }
            client.startDiscovery { _discoveredServers.value = it }
        }
    }

    fun stop() {
        server.stop()
        client.stopDiscovery()
        client.disconnect()
    }

    fun connectToCustomServer(address: String, port: Int) {
        client.addCustomServer(address, port) { _discoveredServers.value = it }
        connect(address, port)
    }

    fun connect(address: String, port: Int) {
        _currentServer.value = address to port
        client.connect(
            address = address,
            port = port,
            onPlayerUpdate = { update ->
                when (update) {
                    is NowPlayingUpdate -> Player.onUpdateSongCommand(update.song)
                    is PlaybackStateUpdate -> Player.onUpdatePlaybackStateCommand(update.isPlaying)
                }
            },
            onConnectionChanged = { connected ->
                _isConnected.value = connected
                if (!connected) _currentServer.value = null
                Player.setRemote(connected)
            },
        )
    }

    fun disconnect() {
        client.disconnect()
        _isConnected.value = false
        _currentServer.value = null
        Player.setRemote(false)
    }

    suspend fun fetchSongsFromCurrentServer(): List<Song>? {
        val (address, port) = _currentServer.value ?: return null
        return try {
            client.fetchSongs(address, port)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Logger.e(e) { "Failed to fetch songs from $address:$port" }
            null
        }
    }

    fun send(command: WebSocketCommand) {
        if (_isConnected.value) client.send(command)
    }

    fun sendPlayerUpdate(update: PlayerUpdate) = server.sendPlayerUpdate(update)
}
