package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * WASM-specific implementation of NetworkClient.
 * Since UDP sockets might not be fully supported in WASM,
 * this is a simplified implementation that provides the necessary functionality.
 */
actual class NetworkClient {
    private val discoveredServers = mutableMapOf<String, Int>() // Map of IP address to port
    private val serverLastSeen = mutableMapOf<String, Long>()
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    // Server connection info
    private var currentServerAddress: String? = null
    private var currentServerPort: Int? = null
    private var lastUpdateId: Long = 0
    private var updatePollingJob: Job? = null

    /**
     * Starts the discovery process.
     * In WASM, we don't discover servers, but we provide the callback for compatibility.
     * @param onServersDiscovered Callback that will be called with the map of discovered servers (IP to port).
     */
    actual fun startDiscovery(onServersDiscovered: (Map<String, Int>) -> Unit) {
        Logger.d { "Starting NetworkClient discovery in WASM (stub implementation)" }
        // Call the callback with an empty map to indicate no servers were discovered
        onServersDiscovered(discoveredServers)
    }

    /**
     * Stops the discovery process.
     * In WASM, this is a no-op.
     */
    actual fun stopDiscovery() {
        Logger.d { "Stopping NetworkClient discovery in WASM (stub implementation)" }
        discoveredServers.clear()
        serverLastSeen.clear()
    }

    /**
     * Connects to a custom server with the given address and port.
     * This is the key functionality we need to implement for WASM.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onServersDiscovered Callback that will be called with the updated map of discovered servers.
     */
    actual fun connectToCustomServer(
        address: String,
        port: Int,
        onServersDiscovered: (Map<String, Int>) -> Unit,
    ) {
        Logger.d { "Connecting to custom server at $address:$port in WASM" }

        // Add the custom server to the discoveredServers map
        discoveredServers[address] = port

        // Update the last-seen timestamp
        serverLastSeen[address] = Clock.System.now().toEpochMilliseconds()

        // Notify about the updated server list
        onServersDiscovered(discoveredServers)
    }

    /**
     * Connects to the WebSocket server at the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onPlayerUpdate Callback that will be called when the player state is updated.
     * @param onConnectionStateChange Callback that will be called when the connection state changes.
     */
    actual fun connectToWebSocket(
        address: String,
        port: Int,
        onPlayerUpdate: (PlayerUpdate) -> Unit,
        onConnectionStateChange: (Boolean) -> Unit,
    ) {
        // Check if already connected
        if (webSocketSession != null) {
            Logger.d { "WebSocket connection already active in WASM" }
            return
        }

        Logger.d { "Connecting to WebSocket server at ws://$address:$port/player in WASM" }

        // Store server connection info
        currentServerAddress = address
        currentServerPort = port

        // Connect to the WebSocket server
        updatePollingJob = scope.launch {
            try {
                httpClient.webSocket(
                    method = HttpMethod.Get,
                    host = address,
                    port = port,
                    path = "/player"
                ) {
                    // Store the session
                    webSocketSession = this

                    // Notify that a connection is established
                    onConnectionStateChange(true)
                    Logger.d { "WebSocket connection established in WASM" }

                    try {
                        // Listen for incoming messages
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                // Deserialize the text to a PlayerUpdate object
                                Json.decodeFromString<PlayerUpdate>(frame.readText()).let { message ->
                                    try {
                                        onPlayerUpdate(message)
                                    } catch (e: Exception) {
                                        Logger.e(e) { "Error deserializing player update: $message" }
                                    }
                                }
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        // Normal close
                        Logger.d { "WebSocket connection closed normally in WASM" }
                        onConnectionStateChange(false)
                    } catch (e: Exception) {
                        Logger.e(e) { "Error in WebSocket connection in WASM" }
                        onConnectionStateChange(false)
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to connect to WebSocket server in WASM: ${e.message}" }
                onConnectionStateChange(false)
            } finally {
                webSocketSession = null
            }
        }
    }

    /**
     * Disconnects from the WebSocket server.
     */
    actual fun disconnectFromWebSocket() {
        // Cancel the polling job
        updatePollingJob?.cancel()
        updatePollingJob = null

        // Close the WebSocket session
        scope.launch {
            try {
                webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
                Logger.d { "WebSocket connection closed in WASM" }
            } catch (e: Exception) {
                Logger.e(e) { "Error closing WebSocket connection in WASM" }
            } finally {
                webSocketSession = null
                currentServerAddress = null
                currentServerPort = null
                lastUpdateId = 0
            }
        }
    }

    /**
     * Sends a command to play a song on the server.
     * @param song The song to play.
     */
    actual fun sendPlaySongCommand(song: Song) {
        val session = webSocketSession ?: run {
            Logger.e { "Cannot send play command: Not connected to a WebSocket server" }
            return
        }

        scope.launch {
            try {
                session.run {
                    sendSerialized<WebSocketCommand>(PlaySongCommand(song))
                    Logger.d { "Play command sent successfully via WebSocket" }
                    val update = receiveDeserialized<PlayerUpdate>()
                    Logger.d { "Received acknowledgment from server: $update" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error sending play command via WebSocket" }
            }
        }
    }

    /**
     * Sends a command to pause playback on the server.
     */
    actual fun sendPauseCommand() {
        sendSimpleCommand(WebSocketCommandType.PAUSE)
    }

    /**
     * Sends a command to resume playback on the server.
     */
    actual fun sendResumeCommand() {
        sendSimpleCommand(WebSocketCommandType.RESUME)
    }

    /**
     * Sends a command to play the next song on the server.
     */
    actual fun sendNextCommand() {
        sendSimpleCommand(WebSocketCommandType.NEXT)
    }

    /**
     * Sends a command to play the previous song on the server.
     */
    actual fun sendPreviousCommand() {
        sendSimpleCommand(WebSocketCommandType.PREVIOUS)
    }

    /**
     * Sends a simple command to the server.
     * @param commandType The updateType of command to send.
     */
    private fun sendSimpleCommand(commandType: WebSocketCommandType) {
        val session = webSocketSession ?: run {
            Logger.e { "Cannot send ${commandType.value} command: Not connected to a WebSocket server" }
            return
        }

        scope.launch {
            try {
                session.run {
                    sendSerialized<WebSocketCommand>(SimpleCommand(commandType))
                    Logger.d { "${commandType.value} command sent successfully via WebSocket" }
                    val update = receiveDeserialized<PlayerUpdate>()
                    Logger.d { "Received acknowledgment from server: $update" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error sending ${commandType.value} command via WebSocket" }
            }
        }
    }
}
