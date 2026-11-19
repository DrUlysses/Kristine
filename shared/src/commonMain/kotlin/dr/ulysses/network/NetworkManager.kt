package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository
import dr.ulysses.player.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ServerInfo(
    val port: Int,
    val addresses: List<String> = emptyList(),
    /** SHA-256 of this server's certificate, which clients pin; null where this device runs no server. */
    val fingerprint: String? = null,
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

    /** False while [currentServer] is still being connected to. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /** The connected server's songs and playlists, shown instead of this device's; null when not connected. */
    private val _remoteLibrary = MutableStateFlow<Library?>(null)
    val remoteLibrary: StateFlow<Library?> = _remoteLibrary.asStateFlow()

    /** A server that presented a different certificate than the one pinned for it. */
    data class CertificateChange(val address: String, val port: Int, val fingerprint: String)

    private val _certificateChange = MutableStateFlow<CertificateChange?>(null)
    val certificateChange: StateFlow<CertificateChange?> = _certificateChange.asStateFlow()

    /** Whether this device plays the server's songs too, instead of only controlling the server. */
    private val _playHere = MutableStateFlow(false)
    val playHere: StateFlow<Boolean> = _playHere.asStateFlow()

    private var lastWindow: QueueWindowUpdate? = null
    private var followJob: Job? = null
    private val songCache = SongCache()

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
        client.addCustomServer(
            address = address,
            port = port
        ) { _discoveredServers.value = it }
        connect(
            address = address,
            port = port
        )
    }

    fun connect(address: String, port: Int) = scope.launch {
        _currentServer.value = address to port
        _certificateChange.value = null
        client.connect(
            address = address,
            port = port,
            pinnedFingerprint = ServerPins.get(address),
            onPlayerUpdate = { update ->
                when (update) {
                    is QueueWindowUpdate -> {
                        lastWindow = update
                        Player.onRemoteWindow(update)
                        if (_playHere.value) {
                            follow(update)
                        }
                    }
                }
            },
            onConnectionChanged = { connected ->
                _isConnected.value = connected
                if (connected) {
                    scope.launch {
                        val library = runCatching { client.fetchLibrary() }.onFailure { Logger.e(it) { "Loading the library of $address failed" } }.getOrNull()
                        // The connection may have dropped while loading
                        if (_isConnected.value) {
                            _remoteLibrary.value = library
                        }
                    }
                } else {
                    _currentServer.value = null
                    _remoteLibrary.value = null
                    stopFollowing()
                }
                Player.setRemote(connected)
            },
            // Trust on first use: the first certificate seen is pinned
            onCertificate = { fingerprint ->
                if (ServerPins.get(address) == null) {
                    ServerPins.save(
                        address = address,
                        fingerprint = fingerprint
                    )
                }
            },
            onCertificateChanged = { fingerprint ->
                _certificateChange.value = CertificateChange(
                    address = address,
                    port = port,
                    fingerprint = fingerprint
                )
            },
        )
    }

    /** Pins the changed certificate, e.g. after the server app was reinstalled, and connects. */
    fun trustChangedCertificate(change: CertificateChange) = scope.launch {
        ServerPins.save(
            address = change.address,
            fingerprint = change.fingerprint
        )
        connect(
            address = change.address,
            port = change.port
        )
    }

    fun dismissCertificateChange() {
        _certificateChange.value = null
    }

    suspend fun remoteArtwork(path: String): ByteArray? = runCatching { client.fetchArtwork(path) }.getOrNull()

    fun disconnect() {
        client.disconnect()
        _isConnected.value = false
        _currentServer.value = null
        _remoteLibrary.value = null
        stopFollowing()
        Player.setRemote(false)
    }

    fun setPlayHere(enabled: Boolean) {
        _playHere.value = enabled
        if (enabled) {
            lastWindow?.let(::follow)
        } else {
            followJob?.cancel()
            Player.followOnDevice(
                song = null,
                isPlaying = false
            )
        }
    }

    // Plays the server's current song here: this library's own copy, else the downloaded file
    private fun follow(window: QueueWindowUpdate) {
        followJob?.cancel()
        followJob = scope.launch {
            try {
                val current = window.current?.let { localCopy(it) }
                Player.followOnDevice(
                    song = current,
                    isPlaying = window.isPlaying
                )
                // The neighbours come ahead of time, so skipping doesn't wait for a download
                listOfNotNull(window.next, window.previous).forEach { localCopy(it) }
                songCache.retain(window.songs.mapTo(HashSet()) { it.path })
            } catch (e: Exception) {
                ensureActive()
                Logger.e(e) { "Playing the server's song here failed" }
            }
        }
    }

    private suspend fun localCopy(song: Song): Song? =
        SongRepository.findMatch(song) ?: songCache.pathFor(song.path) { client.downloadSong(song.path) }?.let {
            song.copy(
                path = it
            )
        }

    private fun stopFollowing() {
        followJob?.cancel()
        lastWindow = null
        scope.launch { songCache.retain(emptySet()) }
    }

    fun send(command: WebSocketCommand) {
        if (_isConnected.value) {
            client.send(command)
        }
    }

    fun sendPlayerUpdate(update: PlayerUpdate) = server.sendPlayerUpdate(update)
}
