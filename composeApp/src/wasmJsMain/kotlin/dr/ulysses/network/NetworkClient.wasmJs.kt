package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * WASM-specific implementation of NetworkClient.
 * Since UDP sockets might not be fully supported in WASM,
 * this is a simplified implementation that provides the necessary functionality.
 */
actual class NetworkClient {
    private val discoveredServers = mutableMapOf<String, Int>() // Map of IP address to port
    private val serverLastSeen = mutableMapOf<String, Long>()
    private val httpClient = HttpClient()
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

        // Update the last seen timestamp
        serverLastSeen[address] = Clock.System.now().toEpochMilliseconds()

        // Notify about the updated server list
        onServersDiscovered(discoveredServers)
    }

    /**
     * Connects to the WebSocket server at the given address and port.
     * In WASM, we use HTTP polling instead of WebSockets.
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
            Logger.d { "Update polling already active in WASM" }
            return
        }

        Logger.d { "Starting update polling for server at $address:$port in WASM" }

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
                                Logger.d { "Received player update in WASM: $update" }
                                onPlayerUpdate(update)
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(e) { "Error polling for updates in WASM" }
                    }

                    // Poll every 1 second
                    delay(1.seconds)
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error in update polling in WASM" }
                onConnectionStateChange(false)
            }
        }
    }

    /**
     * Disconnects from the WebSocket server.
     * In WASM, this stops the HTTP polling.
     */
    actual fun disconnectFromWebSocket() {
        updatePollingJob?.cancel()
        updatePollingJob = null
        currentServerAddress = null
        currentServerPort = null
        lastUpdateId = 0
        Logger.d { "Stopped update polling in WASM" }
    }

    /**
     * Sends a command to play a song on the server.
     * @param song The song to play.
     */
    actual fun sendPlaySongCommand(song: Song) {
        val address = currentServerAddress
        val port = currentServerPort

        if (address == null || port == null) {
            Logger.e { "Cannot send play command in WASM: Not connected to a server" }
            return
        }

        scope.launch {
            try {
                val response = httpClient.post("http://$address:$port/play") {
                    contentType(ContentType.Application.Json)
                    setBody(Json.encodeToString(song))
                }

                if (response.status.isSuccess()) {
                    Logger.d { "Play command sent successfully from WASM" }
                } else {
                    Logger.e { "Failed to send play command from WASM: ${response.status}" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error sending play command from WASM" }
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
            Logger.e { "Cannot send $command command from WASM: Not connected to a server" }
            return
        }

        scope.launch {
            try {
                val response = httpClient.post("http://$address:$port/$command")

                if (response.status.isSuccess()) {
                    Logger.d { "$command command sent successfully from WASM" }
                } else {
                    Logger.e { "Failed to send $command command from WASM: ${response.status}" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error sending $command command from WASM" }
            }
        }
    }
}
