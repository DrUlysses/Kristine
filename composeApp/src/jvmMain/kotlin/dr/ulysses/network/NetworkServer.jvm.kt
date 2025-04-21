package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.SongRepository.getAllSongs
import dr.ulysses.player.Player
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * JVM-specific implementation of NetworkServer.
 * This is the full implementation that uses UDP sockets for broadcasting and HTTP server for API.
 */
actual class NetworkServer {
    actual companion object {
        actual val BROADCAST_MESSAGE_PREFIX = "Kristine Server Discovery:"
        actual val BROADCAST_INTERVAL_SECONDS = 5
        actual val DISCOVERY_PORT = 45678 // Port used only for initial discovery
    }

    private var broadcastJob: Job? = null
    private var httpServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var server: ServerInfo = ServerInfo(0)

    // Player updates
    private val playerUpdates = ConcurrentHashMap<Long, String>()
    private var lastUpdateId = 0L

    // WebSocket connections
    private val webSocketSessions = ConcurrentHashMap<String, DefaultWebSocketSession>()
    private var lastSessionId = 0L

    /**
     * Starts broadcasting UDP packets on the local network.
     * @return The port number the server is listening to on
     */
    actual suspend fun start(): ServerInfo {
        if (broadcastJob != null) return server

        Logger.d { "Starting UDP broadcast server" }

        // Create a socket and get the port synchronously
        val selectorManager = SelectorManager(Dispatchers.Default)
        val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", 0)) {
            broadcast = true
        }

        // Get the dynamically assigned port
        val localAddress = socket.localAddress as InetSocketAddress
        server = ServerInfo(
            port = localAddress.port,
            addresses = listOf(localAddress.hostname)
        )
        Logger.d { "Server bound to: $server" }

        // Start the HTTP server
        startHttpServer()

        // Start broadcasting in a separate coroutine
        broadcastJob = scope.launch {
            try {
                while (isActive) {
                    // Broadcast to localhost
                    sendBroadcast(socket, "127.0.0.1")

                    // Broadcast to 192.168.x.255 (common broadcast addresses)
                    for (subnet in 0..10) {
                        sendBroadcast(socket, "192.168.$subnet.255")
                    }

                    delay(BROADCAST_INTERVAL_SECONDS.seconds)
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error in UDP broadcast server" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }

        return server
    }

    private suspend fun sendBroadcast(socket: BoundDatagramSocket, address: String) {
        try {
            // Create a message with port information
            val broadcastMessage = "$BROADCAST_MESSAGE_PREFIX${server.port}"
            Logger.d { "Broadcasting to $address: $broadcastMessage" }

            socket.send(
                Datagram(
                    packet = ByteReadPacket(broadcastMessage.toByteArray()),
                    address = InetSocketAddress(address, DISCOVERY_PORT)
                )
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to broadcast to $address" }
        }
    }

    /**
     * Starts the HTTP server for handling REST API requests.
     */
    private fun startHttpServer() {
        if (httpServer != null) return

        Logger.d { "Starting HTTP server on: $server" }

        httpServer = embeddedServer(
            factory = CIO,
            port = server.port
        ) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
            install(ContentNegotiation) {
                json()
            }
            routing {
                // WebSocket endpoint for player control and updates
                webSocket("/player") {
                    try {
                        // Generate a unique session ID
                        val sessionId = synchronized(this@NetworkServer) {
                            lastSessionId++
                            "session-$lastSessionId"
                        }

                        // Store the session
                        webSocketSessions[sessionId] = this

                        Logger.d { "WebSocket client connected: $sessionId" }

                        // Send the current player state if available
                        if (playerUpdates.isNotEmpty()) {
                            val latestUpdate = playerUpdates[playerUpdates.keys.maxOrNull() ?: 0]
                            if (latestUpdate != null) {
                                send(Frame.Text(latestUpdate))
                            }
                        }

                        // Listen for incoming messages
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                val message = Json.decodeFromString<WebSocketCommand>(frame.readText())
                                Logger.d { "Received WebSocket message: $message on Android" }

                                try {
                                    when (message.commandType) {
                                        WebSocketCommandType.PLAY -> {
                                            if (message is PlaySongCommand) {
                                                Player.onPlaySongCommand(message.song)
                                            }
                                        }

                                        WebSocketCommandType.PAUSE -> Player.onPauseCommand()
                                        WebSocketCommandType.RESUME -> Player.onResumeCommand()
                                        WebSocketCommandType.NEXT -> Player.onNextCommand()
                                        WebSocketCommandType.PREVIOUS -> Player.onPreviousCommand()
                                        WebSocketCommandType.SET_PLAYLIST -> {
                                            if (message is SetPlaylistCommand) {
                                                // Set the playlist on the server
                                                Player.onSongsChanged(message.songs)
                                                // Set the current track number and play the song at that index
                                                if (message.currentSongIndex >= 0 && message.currentSongIndex < message.songs.size) {
                                                    Player.onPlaySongCommand(message.songs[message.currentSongIndex])
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Logger.e(e) { "Error processing WebSocket command on Android" }
                                }
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        // Normal close
                        Logger.d { "WebSocket client disconnected normally" }
                    } catch (e: Exception) {
                        Logger.e(e) { "WebSocket error" }
                    } finally {
                        // Remove the session when the client disconnects
                        val sessionToRemove = webSocketSessions.entries.find { it.value == this }?.key
                        if (sessionToRemove != null) {
                            webSocketSessions.remove(sessionToRemove)
                            Logger.d { "WebSocket session removed: $sessionToRemove" }
                        }
                    }
                }

                // Get a song list
                get("/songs") {
                    // Add CORS headers to allow cross-origin requests
                    call.response.headers.append("Access-Control-Allow-Origin", "*")
                    call.response.headers.append("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    call.response.headers.append("Access-Control-Allow-Headers", "Content-Type")

                    // Launch a coroutine to call the suspend function
                    call.respond(HttpStatusCode.OK, Json.encodeToString(getAllSongs()))
                }

                // Handle OPTIONS requests for CORS preflight
                options("{...}") {
                    call.response.headers.append("Access-Control-Allow-Origin", "*")
                    call.response.headers.append("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    call.response.headers.append("Access-Control-Allow-Headers", "Content-Type")
                    call.respond(HttpStatusCode.OK)
                }
            }
        }.start(wait = false)

        server = server.copy(
            addresses = NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { networkInterface ->
                    networkInterface.inetAddresses
                        .toList()
                        .filter { inetAddress ->
                            !inetAddress.isLoopbackAddress &&
                                    inetAddress is Inet4Address // For IPv4 only
                        }
                        .map { it.hostAddress }
                }
        )

        Logger.d { "HTTP server started on: $server" }
    }

    /**
     * Stops the UDP broadcast server.
     */
    actual fun stop() {
        broadcastJob?.cancel()
        broadcastJob = null

        httpServer?.stop(1000, 2000)
        httpServer = null

        // Clear player updates
        playerUpdates.clear()

        Logger.d { "Stopped UDP broadcast server and HTTP server" }
    }

    /**
     * Sends a player update to all connected clients.
     * @param update The update object to send.
     */
    actual fun sendPlayerUpdate(update: PlayerUpdate) {
        // Serialize the update object to JSON
        val updateJson = Json.encodeToString<PlayerUpdate>(update)

        // Store the update with a unique ID
        val updateId = synchronized(this) {
            lastUpdateId++
            lastUpdateId
        }
        playerUpdates[updateId] = updateJson

        // Keep only the last 100 updates
        if (playerUpdates.size > 100) {
            val keysToRemove = playerUpdates.keys.sorted().take(playerUpdates.size - 100)
            keysToRemove.forEach { playerUpdates.remove(it) }
        }

        // Send update to all connected WebSocket clients
        if (webSocketSessions.isNotEmpty()) {
            scope.launch {
                val frame = Frame.Text(updateJson)
                webSocketSessions.values.forEach { session ->
                    try {
                        session.send(frame)
                    } catch (e: Exception) {
                        Logger.e(e) { "Failed to send update to WebSocket client" }
                    }
                }
            }
        }

        Logger.d { "Stored and sent player update: $updateJson" }
    }
}
