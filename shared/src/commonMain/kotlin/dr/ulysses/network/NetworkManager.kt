package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.api.external.MpdClient
import dr.ulysses.api.internal.TaskCommand
import dr.ulysses.entities.*
import dr.ulysses.player.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

data class ServerInfo(
    val port: Int,
    val addresses: List<String> = emptyList(),
    /** SHA-256 of this server's certificate, which clients pin; null where this device runs no server. */
    val fingerprint: String? = null,
)

/**
 * Owns this device's server, and its client connection to another device.
 */
object NetworkManager {
    private val server = NetworkServer()
    private val client = NetworkClient()
    private val mpd = MpdClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mpdJob: Job? = null
    private const val MAX_RECENT_SERVERS = 8

    val localServer: StateFlow<ServerInfo>
        field = MutableStateFlow(ServerInfo(0))

    /**
     * Address → port.
     */
    val discoveredServers: StateFlow<Map<String, Int>>
        field = MutableStateFlow<Map<String, Int>>(emptyMap())

    val currentServer: StateFlow<Pair<String, Int>?>
        field = MutableStateFlow<Pair<String, Int>?>(null)

    /**
     * What [currentServer] is, and so what can be done while connected to it.
     */
    val serverType: StateFlow<ServerType>
        field = MutableStateFlow(ServerType.Kristine)

    /**
     * False while [currentServer] is still being connected to.
     */
    val isConnected: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /**
     * The servers connected to lately, the last one first.
     */
    val recentServers: StateFlow<List<RecentServer>>
        field = MutableStateFlow(emptyList<RecentServer>())

    /**
     * Why the connection ended or failed, when the user didn't end it; shown until dismissed.
     */
    val connectionError: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    /**
     * A server that presented a different certificate than the one pinned for it.
     */
    val certificateChange: StateFlow<CertificateChange?>
        field = MutableStateFlow<CertificateChange?>(null)

    /**
     * Whether this device plays the server's songs too, instead of only controlling the server.
     */
    val playHere: StateFlow<Boolean>
        field = MutableStateFlow(false)

    private val isMpd get() = serverType.value == ServerType.Mpd

    // The user disconnected or switched servers: the connection's end is no error
    @Volatile
    private var endRequested = false

    private var lastWindow: QueueWindowUpdate? = null
    private var followJob: Job? = null
    private val songCache = SongCache()

    fun start() {
        scope.launch {
            try {
                localServer.value = server.start()
            } catch (e: Exception) {
                ensureActive()
                // The app still works as a client and as a local player
                Logger.e(e) { "Local server failed to start" }
            }
            client.startDiscovery { discoveredServers.value = it }
        }
        scope.launch {
            recentServers.value = runCatching {
                SettingsRepository.get(SettingKey.RecentServers)?.value?.let {
                    Json.decodeFromString<List<RecentServer>>(it)
                }
            }
                .onFailure { currentCoroutineContext().ensureActive() }
                .getOrNull()
                .orEmpty()
        }
    }

    /** Puts [server] first in [recentServers], or takes it out when [keep] is false. */
    fun rememberServer(
        server: RecentServer,
        keep: Boolean = true,
    ) = scope.launch {
        val others = recentServers.value.filter { it.address != server.address || it.port != server.port }
        val servers = if (keep) {
            (listOf(server) + others).take(MAX_RECENT_SERVERS)
        } else {
            others
        }
        recentServers.value = servers
        SettingsRepository.upsert(
            Setting(
                key = SettingKey.RecentServers,
                value = Json.encodeToString(servers)
            )
        )
    }

    fun stop() {
        server.stop()
        client.stopDiscovery()
        client.disconnect()
    }

    fun connectToCustomServer(
        address: String,
        port: Int,
    ) {
        client.addCustomServer(
            address = address,
            port = port
        ) { discoveredServers.value = it }
        connect(
            address = address,
            port = port
        )
    }

    fun connect(
        address: String,
        port: Int,
    ) = scope.launch {
        // The connection before ends first, so its end can't reset this one
        endRequested = true
        mpdJob?.cancelAndJoin()
        client.disconnectAndWait()
        endRequested = false
        connectionError.value = null
        serverType.value = ServerType.Kristine
        currentServer.value = address to port
        certificateChange.value = null
        client.connect(
            address = address,
            port = port,
            pinnedFingerprint = ServerPins.get(address),
            onPlayerUpdate = { update ->
                when (update) {
                    is QueueWindowUpdate -> {
                        lastWindow = update
                        Player.onRemoteWindow(update)
                        if (playHere.value) {
                            follow(update)
                        }
                    }
                }
            },
            onConnectionChanged = { connected ->
                isConnected.value = connected
                if (connected) {
                    rememberServer(
                        RecentServer(
                            address = address,
                            port = port,
                            type = ServerType.Kristine
                        )
                    )
                }
                if (!connected) {
                    currentServer.value = null
                    stopFollowing()
                    // A changed certificate has its own card
                    if (!endRequested && connectionError.value == null && certificateChange.value == null) {
                        connectionError.value = "The connection to $address ended"
                    }
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
                certificateChange.value = CertificateChange(
                    address = address,
                    port = port,
                    fingerprint = fingerprint
                )
            },
            onFailed = { reason ->
                if (!endRequested) {
                    connectionError.value = "The connection to $address failed: $reason"
                }
            },
        )
    }

    /**
     * Connects to an MPD server as its client, like `mpc`; the lists, the queue and the player are then MPD's.
     */
    fun connectToMpd(
        address: String,
        port: Int,
        password: String?,
    ) {
        val previous = mpdJob
        mpdJob = scope.launch {
            // The connection before ends first, so its end can't reset this one
            endRequested = true
            previous?.cancelAndJoin()
            client.disconnectAndWait()
            endRequested = false
            connectionError.value = null
            serverType.value = ServerType.Mpd
            currentServer.value = address to port
            certificateChange.value = null
            try {
                mpd.connect(
                    address = address,
                    port = port,
                    password = password,
                    onConnected = {
                        isConnected.value = true
                        Player.setRemote(true)
                        rememberServer(
                            RecentServer(
                                address = address,
                                port = port,
                                type = ServerType.Mpd
                            )
                        )
                    },
                    onWindow = Player::onRemoteWindow
                )
            } catch (e: Exception) {
                ensureActive()
                Logger.e(e) { "MPD connection to $address:$port failed" }
                connectionError.value = "The connection to $address failed: ${e.message ?: e::class.simpleName}"
            } finally {
                // Also when the server closes the connection, so the app falls back to its own library
                isConnected.value = false
                currentServer.value = null
                serverType.value = ServerType.Kristine
                Player.setRemote(false)
            }
        }
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
        certificateChange.value = null
    }

    suspend fun remoteArtwork(path: String): ByteArray? = runCatching {
        if (isMpd) {
            mpd.artwork(path)
        } else {
            client.fetchArtwork(path)
        }
    }
        .onFailure { currentCoroutineContext().ensureActive() }
        .getOrNull()

    internal suspend fun callLibrary(
        route: String,
        body: String,
    ): String = if (isMpd) {
        mpd.answer(
            route = route,
            body = body
        )
    } else {
        client.callLibrary(
            route = route,
            body = body
        )
    }

    // MPD runs no long library tasks; its screens are disabled
    internal fun sendTask(command: TaskCommand) {
        if (!isMpd) {
            client.sendTask(command)
        }
    }

    fun dismissConnectionError() {
        connectionError.value = null
    }

    fun disconnect() {
        endRequested = true
        client.disconnect()
        mpdJob?.cancel()
        isConnected.value = false
        currentServer.value = null
        stopFollowing()
        Player.setRemote(false)
    }

    fun setPlayHere(enabled: Boolean) {
        playHere.value = enabled
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
                // The neighbors come ahead of time, so skipping doesn't wait for a download
                listOfNotNull(window.next, window.previous).forEach { localCopy(it) }
                songCache.retain(window.songs.mapTo(HashSet()) { it.path })
            } catch (e: Exception) {
                ensureActive()
                Logger.e(e) { "Playing the server's song here failed" }
            }
        }
    }

    /**
     * The file of the connected server's song at [path].
     */
    suspend fun downloadSong(path: String): ByteArray = client.downloadSong(path)

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
        when {
            !isConnected.value -> Unit
            isMpd -> mpd.send(command)
            else -> client.send(command)
        }
    }

    fun sendPlayerUpdate(update: PlayerUpdate) = server.sendPlayerUpdate(update)

    /**
     * A server that presented a different certificate than the one pinned for it.
     */
    data class CertificateChange(
        val address: String,
        val port: Int,
        val fingerprint: String,
    )
}
