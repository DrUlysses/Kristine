package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.SongRepository
import dr.ulysses.entities.localFilePath
import dr.ulysses.player.Player
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import java.net.NetworkInterface

actual class NetworkServer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var broadcastJob: Job? = null
    private var httpServer: EmbeddedServer<*, *>? = null
    private var tlsFront: TlsFront? = null
    private var server = ServerInfo(0)
    private val sessions = MutableStateFlow(emptySet<DefaultWebSocketSession>())

    // A client that connects later gets the window right away
    @Volatile
    private var lastWindow: String? = null

    // Only these songs can be downloaded: the ones clients were told about
    @Volatile
    private var windowPaths: Set<String> = emptySet()

    actual suspend fun start(): ServerInfo {
        if (broadcastJob != null) {
            return server
        }

        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).udp().bind(
            InetSocketAddress(
                hostname = "0.0.0.0",
                port = 0
            )
        ) { broadcast = true }
        val port = (socket.localAddress as InetSocketAddress).port
        startHttpServer(port)
        server = ServerInfo(
            port = port,
            addresses = localIpv4Addresses(),
            fingerprint = ServerCertificate.fingerprint
        )
        Logger.d { "Server started: $server" }

        val announcement = "$BROADCAST_MESSAGE_PREFIX$port".encodeToByteArray()
        broadcastJob = scope.launch {
            try {
                while (isActive) {
                    // 255.255.255.255 reaches every device on the local network, whatever its subnet;
                    // loopback finds a second instance on the same machine
                    for (address in listOf("255.255.255.255", "127.0.0.1")) {
                        try {
                            socket.send(
                                Datagram(
                                    packet = ByteReadPacket(announcement),
                                    address = InetSocketAddress(
                                        hostname = address,
                                        port = DISCOVERY_PORT
                                    )
                                )
                            )
                        } catch (e: Exception) {
                            ensureActive()
                            Logger.e(e) { "Broadcast to $address failed" }
                        }
                    }
                    delay(BROADCAST_INTERVAL)
                }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }
        return server
    }

    // TLS only: the plain server listens on loopback, and the LAN reaches it through the TLS front on [port].
    // Clients pin the certificate's fingerprint on their first connection
    private suspend fun startHttpServer(port: Int) {
        val server = embeddedServer(
            factory = CIO,
            host = "127.0.0.1",
            port = 0
        ) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            routing {
                webSocket("/player") {
                    sessions.update { it + this }
                    try {
                        lastWindow?.let { send(Frame.Text(it)) }
                        for (frame in incoming) {
                            if (frame !is Frame.Text) {
                                continue
                            }
                            // A malformed or unknown command must not drop the client
                            runCatching { Json.decodeFromString<WebSocketCommand>(frame.readText()) }
                                .onSuccess { handle(it) }
                                .onFailure { Logger.e(it) { "Ignoring malformed command" } }
                        }
                    } finally {
                        sessions.update { it - this }
                    }
                }
                get(SONG_ROUTE) {
                    val path = call.request.queryParameters["path"]
                    if (path == null || path !in windowPaths) {
                        return@get call.respond(HttpStatusCode.NotFound)
                    }
                    // shortcut: the whole file is read into memory, fine for one song at a time; stream it if many clients download at once
                    val bytes = withContext(Dispatchers.IO) {
                        SystemFileSystem.source(Path(localFilePath(path))).buffered().use { it.readByteArray() }
                    }
                    call.response.header(
                        name = HttpHeaders.AccessControlAllowOrigin,
                        value = "*"
                    )
                    call.respondBytes(
                        bytes = bytes,
                        contentType = ContentType.Application.OctetStream
                    )
                }
            }
        }.start(
            wait = false
        )
        httpServer = server
        tlsFront = TlsFront(
            keyStore = ServerCertificate.keyStore,
            password = ServerCertificate.PASSWORD.toCharArray(),
            port = port,
            backendPort = server.engine.resolvedConnectors().first().port
        ).also { it.start(scope) }
    }

    private suspend fun handle(command: WebSocketCommand) {
        // Anyone on the LAN can send commands, so only songs from this library are played; a client sends its own
        // copies, which are looked up here
        when (command) {
            is PlaySongCommand -> SongRepository.findMatch(command.song)?.let { Player.onPlaySongCommand(it) }

            is SetPlaylistCommand -> {
                val current = command.songs.getOrNull(command.currentSongIndex)?.let { SongRepository.findMatch(it) }
                Player.onSongsChanged(command.songs.mapNotNull { SongRepository.findMatch(it) })
                current?.let { Player.onPlaySongCommand(it) }
            }

            PauseCommand -> Player.onPauseCommand()
            ResumeCommand -> Player.onResumeCommand()
            NextCommand -> Player.onNextCommand()
            PreviousCommand -> Player.onPreviousCommand()
        }
    }

    actual fun stop() {
        broadcastJob?.cancel()
        broadcastJob = null
        tlsFront?.stop()
        tlsFront = null
        httpServer?.stop(
            gracePeriodMillis = 1000,
            timeoutMillis = 2000
        )
        httpServer = null
    }

    actual fun sendPlayerUpdate(update: PlayerUpdate) {
        val json = Json.encodeToString<PlayerUpdate>(update)
        when (update) {
            is QueueWindowUpdate -> {
                lastWindow = json
                windowPaths = update.songs.mapTo(HashSet()) { it.path }
            }
        }
        val clients = sessions.value
        if (clients.isEmpty()) {
            return
        }
        scope.launch {
            clients.forEach { session ->
                try {
                    session.send(Frame.Text(json))
                } catch (e: Exception) {
                    ensureActive()
                    Logger.e(e) { "Failed to send update to a client" }
                }
            }
        }
    }

    // shortcut: Ktor has no API to list network interfaces, so this one call stays on java.net
    private fun localIpv4Addresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filter { !it.isLoopbackAddress && it.address.size == 4 }
        .map { it.address.toIpv4() }
}

private fun ByteArray.toIpv4() = joinToString(".") { (it.toInt() and 0xff).toString() }

internal actual fun serverAnnouncements(): Flow<Pair<String, Int>> = flow {
    val releaseLock = acquireMulticastLock()
    val selectorManager = SelectorManager(Dispatchers.IO)
    val socket = aSocket(selectorManager).udp().bind(
        InetSocketAddress(
            hostname = "0.0.0.0",
            port = DISCOVERY_PORT
        )
    )
    try {
        while (true) {
            val datagram = socket.receive()
            val message = datagram.packet.readText()
            val port = message.removePrefix(BROADCAST_MESSAGE_PREFIX).toIntOrNull()
            if (!message.startsWith(BROADCAST_MESSAGE_PREFIX) || port == null || port <= 0) {
                continue
            }
            // The raw IP: hostname could trigger a reverse DNS lookup
            val address = (datagram.address as? InetSocketAddress)?.resolveAddress()?.takeIf { it.size == 4 }?.toIpv4()
            if (address != null && address != "0.0.0.0" && address != "255.255.255.255") {
                emit(address to port)
            }
        }
    } finally {
        socket.close()
        selectorManager.close()
        releaseLock()
    }
}.flowOn(Dispatchers.IO)

/** Android's Wi-Fi drops broadcast packets unless the app holds a multicast lock. Returns the release. */
internal expect fun acquireMulticastLock(): () -> Unit
