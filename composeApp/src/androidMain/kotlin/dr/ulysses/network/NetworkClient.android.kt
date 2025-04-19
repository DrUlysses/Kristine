package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Android-specific implementation of NetworkClient.
 * This implementation is similar to the JVM implementation but with Android-specific considerations.
 */
actual class NetworkClient {
    private var discoveryJob: Job? = null
    private var updatePollingJob: Job? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private val httpClient = HttpClient {
        install(WebSockets)
    }
    private val scope = CoroutineScope(Dispatchers.Default)
    private val discoveredServers = mutableMapOf<String, Int>() // Map of IP address to port
    private val serverTimeout = 15.seconds.inWholeMilliseconds

    // Server connection info
    private var currentServerAddress: String? = null
    private var currentServerPort: Int? = null
    private var lastUpdateId: Long = 0

    // Map to track when servers were last seen
    private val serverLastSeen = mutableMapOf<String, Long>()

    /**
     * Starts the discovery process to find servers on the local network.
     * @param onServersDiscovered Callback that will be called with the map of discovered servers (IP to port).
     */
    actual fun startDiscovery(onServersDiscovered: (Map<String, Int>) -> Unit) {
        if (discoveryJob != null) return

        Logger.d { "Starting UDP server discovery process on Android" }
        discoveryJob = scope.launch {
            val selectorManager = SelectorManager(Dispatchers.Default)
            val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", NetworkServer.DISCOVERY_PORT))

            try {
                // Start a periodic job to clean up stale servers
                val cleanupJob = launch {
                    while (isActive) {
                        cleanupStaleServers()
                        onServersDiscovered(discoveredServers)
                        delay(5.seconds)
                    }
                }

                // Listen for incoming UDP broadcasts
                while (isActive) {
                    val datagram = socket.receive()
                    val message = datagram.packet.readText()
                    // Extract IP address from the socket address string
                    val addressStr = datagram.address.toString()
                    val senderAddress = addressStr.substringBefore(':').replace("/", "")

                    // Check if the message starts with the broadcast prefix
                    if (message.startsWith(NetworkServer.BROADCAST_MESSAGE_PREFIX)) {
                        val currentTime = Clock.System.now().toEpochMilliseconds()

                        // Extract the port from the message
                        val serverPort =
                            message.substring(NetworkServer.BROADCAST_MESSAGE_PREFIX.length).toIntOrNull() ?: 0

                        if (serverPort > 0) {
                            // Use the actual sender IP instead of the broadcast address
                            if (senderAddress != "0.0.0.0" && senderAddress != "255.255.255.255") {
                                Logger.d { "Server discovered at: $senderAddress:$serverPort" }

                                // Store the server with its port
                                discoveredServers[senderAddress] = serverPort

                                // Update the last seen timestamp
                                serverLastSeen[senderAddress] = currentTime

                                // Notify about the updated server list
                                onServersDiscovered(discoveredServers)
                            }
                        }
                    }
                }

                cleanupJob.cancel()
            } catch (e: Exception) {
                Logger.e(e) { "Error in UDP discovery client on Android" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }
    }

    /**
     * Remove servers that haven't been seen recently
     */
    private fun cleanupStaleServers() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val staleServers = serverLastSeen.filter { (_, lastSeen) ->
            (currentTime - lastSeen) > serverTimeout
        }.keys

        if (staleServers.isNotEmpty()) {
            Logger.d { "Removing stale servers: $staleServers" }
            // Remove each stale server from the discoveredServers map
            staleServers.forEach {
                discoveredServers.remove(it)
                serverLastSeen.remove(it)
            }
        }
    }

    /**
     * Stops the discovery process.
     */
    actual fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        discoveredServers.clear()
        serverLastSeen.clear()
        Logger.d { "Stopped UDP server discovery process on Android" }
    }

    /**
     * Connects to a custom server with the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onServersDiscovered Callback that will be called with the updated map of discovered servers.
     */
    actual fun connectToCustomServer(address: String, port: Int, onServersDiscovered: (Map<String, Int>) -> Unit) {
        Logger.d { "Connecting to custom server at $address:$port on Android" }

        // Add the custom server to the discoveredServers map
        discoveredServers[address] = port

        // Update the last seen timestamp
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
            Logger.d { "WebSocket connection already active" }
            return
        }

        Logger.d { "Connecting to WebSocket server at ws://$address:$port/player" }

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

                    // Notify that connection is established
                    onConnectionStateChange(true)
                    Logger.d { "WebSocket connection established" }

                    try {
                        // Listen for incoming messages
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    Logger.d { "Received WebSocket message: $text" }
                                    try {
                                        // Deserialize the text to a PlayerUpdate object
                                        val playerUpdate = Json.decodeFromString<PlayerUpdate>(text)
                                        onPlayerUpdate(playerUpdate)
                                    } catch (e: Exception) {
                                        Logger.e(e) { "Error deserializing player update: $text" }
                                    }
                                }

                                else -> {
                                    // Ignore other frame types
                                }
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) {
                        // Normal close
                        Logger.d { "WebSocket connection closed normally" }
                        onConnectionStateChange(false)
                    } catch (e: Exception) {
                        Logger.e(e) { "Error in WebSocket connection" }
                        onConnectionStateChange(false)
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to connect to WebSocket server: ${e.message}" }
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
                Logger.d { "WebSocket connection closed" }
            } catch (e: Exception) {
                Logger.e(e) { "Error closing WebSocket connection" }
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
        if (webSocketSession == null) {
            Logger.e { "Cannot send play command: Not connected to a WebSocket server" }
            return
        }

        scope.launch {
            try {
                val command = Json.encodeToString<WebSocketCommand>(PlaySongCommand(song))
                webSocketSession?.send(Frame.Text(command))
                Logger.d { "Play command sent successfully via WebSocket" }
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
        if (webSocketSession == null) {
            Logger.e { "Cannot send ${commandType.value} command: Not connected to a WebSocket server" }
            return
        }

        scope.launch {
            try {
                webSocketSession?.sendSerialized<WebSocketCommand>(SimpleCommand(commandType))
                Logger.d { "${commandType.value} command sent successfully via WebSocket" }
            } catch (e: Exception) {
                Logger.e(e) { "Error sending ${commandType.value} command via WebSocket" }
            }
        }
    }
}
