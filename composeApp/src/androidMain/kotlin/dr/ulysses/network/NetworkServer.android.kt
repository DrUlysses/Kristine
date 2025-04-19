package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import dr.ulysses.entities.SongRepository.getAllSongs
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Android-specific implementation of NetworkServer.
 * This implementation is similar to the JVM implementation but with Android-specific considerations.
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
    private var serverPort: Int = 0

    // Player control callbacks
    private var onPlaySongCommandCallback: ((Song) -> Unit)? = null
    private var onPauseCommandCallback: (() -> Unit)? = null
    private var onResumeCommandCallback: (() -> Unit)? = null
    private var onNextCommandCallback: (() -> Unit)? = null
    private var onPreviousCommandCallback: (() -> Unit)? = null

    // Player updates
    private val playerUpdates = ConcurrentHashMap<Long, String>()
    private var lastUpdateId = 0L

    // WebSocket connections
    private val webSocketSessions = ConcurrentHashMap<String, DefaultWebSocketSession>()
    private var lastSessionId = 0L

    /**
     * Starts broadcasting UDP packets on the local network.
     * @return The port number the server is listening on
     */
    actual suspend fun start(): Int {
        if (broadcastJob != null) return serverPort

        Logger.d { "Starting UDP broadcast server on Android" }

        // Create a socket and get the port synchronously
        val selectorManager = SelectorManager(Dispatchers.Default)
        val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", 0)) {
            broadcast = true
        }

        // Get the dynamically assigned port
        val localAddress = socket.localAddress as InetSocketAddress
        serverPort = localAddress.port
        Logger.d { "Server bound to port: $serverPort on Android" }

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
                Logger.e(e) { "Error in UDP broadcast server on Android" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }

        return serverPort
    }

    private suspend fun sendBroadcast(socket: BoundDatagramSocket, address: String) {
        try {
            // Create a message with port information
            val broadcastMessage = "$BROADCAST_MESSAGE_PREFIX$serverPort"
            Logger.d { "Broadcasting to $address: $broadcastMessage on Android" }

            socket.send(
                Datagram(
                    packet = ByteReadPacket(broadcastMessage.toByteArray()),
                    address = InetSocketAddress(address, DISCOVERY_PORT)
                )
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to broadcast to $address on Android" }
        }
    }

    /**
     * Starts the HTTP server for handling REST API requests.
     */
    private fun startHttpServer() {
        if (httpServer != null) return

        Logger.d { "Starting HTTP server on port: $serverPort on Android" }

        httpServer = embeddedServer(
            factory = CIO,
            port = serverPort
        ) {
            install(WebSockets)
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

                        Logger.d { "WebSocket client connected: $sessionId on Android" }

                        // Send the current player state if available
                        if (playerUpdates.isNotEmpty()) {
                            val latestUpdate = playerUpdates[playerUpdates.keys.maxOrNull() ?: 0]
                            if (latestUpdate != null) {
                                send(Frame.Text(latestUpdate))
                            }
                        }

                        // Listen for incoming messages
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    Logger.d { "Received WebSocket message: $text on Android" }

                                    try {
                                        val command = Json.decodeFromString<WebSocketCommand>(text)
                                        when (command.commandType) {
                                            WebSocketCommandType.PLAY -> {
                                                if (command is PlaySongCommand) {
                                                    onPlaySongCommandCallback?.invoke(command.song)
                                                }
                                            }

                                            WebSocketCommandType.PAUSE -> onPauseCommandCallback?.invoke()
                                            WebSocketCommandType.RESUME -> onResumeCommandCallback?.invoke()
                                            WebSocketCommandType.NEXT -> onNextCommandCallback?.invoke()
                                            WebSocketCommandType.PREVIOUS -> onPreviousCommandCallback?.invoke()
                                        }
                                    } catch (e: Exception) {
                                        Logger.e(e) { "Error processing WebSocket command on Android" }
                                    }
                                }

                                else -> {}
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        // Normal close
                        Logger.d { "WebSocket client disconnected normally on Android" }
                    } catch (e: Exception) {
                        Logger.e(e) { "WebSocket error on Android" }
                    } finally {
                        // Remove the session when the client disconnects
                        val sessionToRemove = webSocketSessions.entries.find { it.value == this }?.key
                        if (sessionToRemove != null) {
                            webSocketSessions.remove(sessionToRemove)
                            Logger.d { "WebSocket session removed: $sessionToRemove on Android" }
                        }
                    }
                }

                // Get songs list
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

        Logger.d { "HTTP server started on port: $serverPort on Android" }
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

        Logger.d { "Stopped UDP broadcast server and HTTP server on Android" }
    }

    /**
     * Starts the WebSocket server for real-time communication.
     * @param onPlaySongCommand Callback that will be called when a client sends a play song command.
     * @param onPauseCommand Callback that will be called when a client sends a pause command.
     * @param onResumeCommand Callback that will be called when a client sends a resume command.
     * @param onNextCommand Callback that will be called when a client sends a next command.
     * @param onPreviousCommand Callback that will be called when a client sends a previous command.
     */
    actual fun startWebSocketServer(
        onPlaySongCommand: (Song) -> Unit,
        onPauseCommand: () -> Unit,
        onResumeCommand: () -> Unit,
        onNextCommand: () -> Unit,
        onPreviousCommand: () -> Unit,
    ) {
        // Store the callbacks
        onPlaySongCommandCallback = onPlaySongCommand
        onPauseCommandCallback = onPauseCommand
        onResumeCommandCallback = onResumeCommand
        onNextCommandCallback = onNextCommand
        onPreviousCommandCallback = onPreviousCommand

        Logger.d { "Player control callbacks registered on Android" }
    }

    /**
     * Stops the WebSocket server.
     */
    actual fun stopWebSocketServer() {
        // Clear the callbacks
        onPlaySongCommandCallback = null
        onPauseCommandCallback = null
        onResumeCommandCallback = null
        onNextCommandCallback = null
        onPreviousCommandCallback = null

        Logger.d { "Player control callbacks cleared on Android" }
    }

    /**
     * Sends a player update to all connected clients.
     * @param update The update object to send.
     */
    actual fun sendPlayerUpdate(update: PlayerUpdate) {
        // Serialize the update object to JSON
        val updateJson = Json.encodeToString(PlayerUpdate.serializer(), update)

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

        Logger.d { "Stored player update: $updateJson on Android" }

        // Send update to all connected WebSocket clients
        if (webSocketSessions.isNotEmpty()) {
            scope.launch {
                webSocketSessions.values.forEach { session ->
                    try {
                        session.send(Frame.Text(updateJson))
                    } catch (e: Exception) {
                        Logger.e(e) { "Failed to send update to WebSocket client on Android" }
                    }
                }
            }
        }
    }
}
