package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * JVM-specific implementation of NetworkClient.
 * This is the full implementation that uses UDP sockets for discovery.
 */
actual class NetworkClient {
    private var discoveryJob: Job? = null
    private var updatePollingJob: Job? = null
    private val httpClient = HttpClient()
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

        Logger.d { "Starting UDP server discovery process" }
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
                Logger.e(e) { "Error in UDP discovery client" }
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
        Logger.d { "Stopped UDP server discovery process" }
    }

    /**
     * Connects to a custom server with the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onServersDiscovered Callback that will be called with the updated map of discovered servers.
     */
    actual fun connectToCustomServer(address: String, port: Int, onServersDiscovered: (Map<String, Int>) -> Unit) {
        Logger.d { "Connecting to custom server at $address:$port" }

        // Add the custom server to the discoveredServers map
        discoveredServers[address] = port

        // Update the last seen timestamp
        serverLastSeen[address] = Clock.System.now().toEpochMilliseconds()

        // Notify about the updated server list
        onServersDiscovered(discoveredServers)
    }

    /**
     * Connects to the WebSocket server at the given address and port.
     * In JVM, we use HTTP polling instead of WebSockets.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onPlayerUpdate Callback that will be called when the player state is updated.
     * @param onConnectionStateChange Callback that will be called when the connection state changes.
     */
    actual fun connectToWebSocket(
        address: String,
        port: Int,
        onPlayerUpdate: (String) -> Unit,
        onConnectionStateChange: (Boolean) -> Unit,
    ) {
        if (updatePollingJob != null) {
            Logger.d { "Update polling already active" }
            return
        }

        Logger.d { "Starting update polling for server at $address:$port" }

        // Store server connection info
        currentServerAddress = address
        currentServerPort = port

        // Notify that connection is established
        onConnectionStateChange(true)

        // Start polling for updates
        updatePollingJob = scope.launch {
            try {
                while (isActive) {
                    try {
                        val response = httpClient.get("http://$address:$port/updates?lastId=$lastUpdateId")
                        if (response.status.isSuccess()) {
                            val responseText = response.bodyAsText()
                            val updateData = Json.decodeFromString<Map<String, Any>>(responseText)

                            // Update lastUpdateId
                            val newLastId = (updateData["lastId"] as? Double)?.toLong() ?: lastUpdateId
                            lastUpdateId = newLastId

                            // Process updates
                            @Suppress("UNCHECKED_CAST")
                            val updates = updateData["updates"] as? List<String> ?: emptyList()
                            updates.forEach { update ->
                                Logger.d { "Received player update: $update" }
                                onPlayerUpdate(update)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(e) { "Error polling for updates" }
                    }

                    // Poll every 1 second
                    delay(1.seconds)
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error in update polling" }
                onConnectionStateChange(false)
            }
        }
    }

    /**
     * Disconnects from the WebSocket server.
     * In JVM, this stops the HTTP polling.
     */
    actual fun disconnectFromWebSocket() {
        updatePollingJob?.cancel()
        updatePollingJob = null
        currentServerAddress = null
        currentServerPort = null
        lastUpdateId = 0
        Logger.d { "Stopped update polling" }
    }

    /**
     * Sends a command to play a song on the server.
     * @param song The song to play.
     */
    actual fun sendPlaySongCommand(song: Song) {
        val address = currentServerAddress
        val port = currentServerPort

        if (address == null || port == null) {
            Logger.e { "Cannot send play command: Not connected to a server" }
            return
        }

        scope.launch {
            try {
                val response = httpClient.post("http://$address:$port/play") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(song))
                }

                if (response.status.isSuccess()) {
                    Logger.d { "Play command sent successfully" }
                } else {
                    Logger.e { "Failed to send play command: ${response.status}" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error sending play command" }
            }
        }
    }

    /**
     * Sends a command to pause playback on the server.
     */
    actual fun sendPauseCommand() {
        sendSimpleCommand("pause")
    }

    /**
     * Sends a command to resume playback on the server.
     */
    actual fun sendResumeCommand() {
        sendSimpleCommand("resume")
    }

    /**
     * Sends a command to play the next song on the server.
     */
    actual fun sendNextCommand() {
        sendSimpleCommand("next")
    }

    /**
     * Sends a command to play the previous song on the server.
     */
    actual fun sendPreviousCommand() {
        sendSimpleCommand("previous")
    }

    /**
     * Sends a simple command to the server.
     * @param command The command to send.
     */
    private fun sendSimpleCommand(command: String) {
        val address = currentServerAddress
        val port = currentServerPort

        if (address == null || port == null) {
            Logger.e { "Cannot send $command command: Not connected to a server" }
            return
        }

        scope.launch {
            try {
                val response = httpClient.post("http://$address:$port/$command")

                if (response.status.isSuccess()) {
                    Logger.d { "$command command sent successfully" }
                } else {
                    Logger.e { "Failed to send $command command: ${response.status}" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error sending $command command" }
            }
        }
    }
}
