package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import dr.ulysses.models.PlayerService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Singleton manager for network-related functionality.
 * Provides access to discovered servers and manages the server and client instances.
 */
object NetworkManager {
    private val server = NetworkServer()
    private val client = NetworkClient()
    private val scope = CoroutineScope(Dispatchers.Default)

    // StateFlow to hold the local server port
    private val _localServerPort = MutableStateFlow(0)
    val localServerPort: StateFlow<Int> = _localServerPort.asStateFlow()

    // StateFlow to hold the map of discovered servers (IP to port)
    private val _discoveredServers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val discoveredServers: StateFlow<Map<String, Int>> = _discoveredServers.asStateFlow()

    // StateFlow to hold the current connected server (IP and port)
    private val _currentServer = MutableStateFlow<Pair<String, Int>?>(null)
    val currentServer: StateFlow<Pair<String, Int>?> = _currentServer.asStateFlow()

    // StateFlow to hold the connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Starts the server and client for network discovery.
     */
    fun start() {
        scope.launch {
            // Start the server and get the dynamically assigned port
            val port = server.start()
            _localServerPort.value = port

            // Start the WebSocket server with callbacks to the PlayerService
            server.startWebSocketServer(
                onPlaySongCommand = { song ->
                    PlayerService.onPlaySongCommand(song)
                },
                onPauseCommand = {
                    PlayerService.onPauseCommand()
                },
                onResumeCommand = {
                    PlayerService.onResumeCommand()
                },
                onNextCommand = {
                    PlayerService.onNextCommand()
                },
                onPreviousCommand = {
                    PlayerService.onPreviousCommand()
                }
            )

            // Start the client with the updated callback
            client.startDiscovery { serverMap ->
                _discoveredServers.value = serverMap
            }
        }
    }

    /**
     * Stops the server and client.
     */
    fun stop() {
        server.stop()
        server.stopWebSocketServer()
        client.stopDiscovery()
        client.disconnectFromWebSocket()
    }

    /**
     * Connects to a custom server with the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     */
    fun connectToCustomServer(address: String, port: Int) {
        scope.launch {
            client.connectToCustomServer(address, port) { serverMap ->
                _discoveredServers.value = serverMap
                _currentServer.value = address to port
            }
        }
    }

    /**
     * Connects to a discovered server with the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @return True if connection was successful, false otherwise.
     */
    fun connectToDiscoveredServer(address: String, port: Int): Boolean {
        return try {
            // Set the current server
            _currentServer.value = address to port

            // Connect to the WebSocket server
            client.connectToWebSocket(
                address = address,
                port = port,
                onPlayerUpdate = { update ->
                    // Process player updates from the server
                    processPlayerUpdate(update)
                },
                onConnectionStateChange = { isConnected ->
                    _isConnected.value = isConnected
                }
            )

            true
        } catch (_: Exception) {
            // Reset the current server if connection failed
            _currentServer.value = null
            false
        }
    }

    /**
     * Disconnects from the current server.
     * @return The address of the server that was disconnected from, or null if there was no current server.
     */
    fun disconnectFromServer(): String? {
        val currentServer = _currentServer.value
        _currentServer.value = null

        // Disconnect from the WebSocket server
        client.disconnectFromWebSocket()
        _isConnected.value = false

        return currentServer?.first
    }

    /**
     * Fetches songs from the current server.
     * @return List of songs from the server, or null if there is no current server or an error occurred.
     */
    suspend fun fetchSongsFromCurrentServer(): List<Song>? {
        val currentServer = _currentServer.value ?: return null
        val (address, port) = currentServer

        return try {
            // Make request to the server's /songs endpoint
            val response: HttpResponse = HttpClient().get("http://$address:$port/songs")

            // Parse the response body as a list of songs
            val responseBody = response.bodyAsText()
            Json.decodeFromString<List<Song>>(responseBody)
        } catch (e: Exception) {
            // Log the error and return null
            Logger.e(e) { "Failed to fetch songs from server: $address:$port" }
            null
        }
    }

    /**
     * Plays a song on the server.
     * @param song The song to play.
     */
    fun playSongOnServer(song: Song) {
        if (_isConnected.value) {
            client.sendPlaySongCommand(song)
        }
    }

    /**
     * Pauses playback on the server.
     */
    fun pausePlaybackOnServer() {
        if (_isConnected.value) {
            client.sendPauseCommand()
        }
    }

    /**
     * Resumes playback on the server.
     */
    fun resumePlaybackOnServer() {
        if (_isConnected.value) {
            client.sendResumeCommand()
        }
    }

    /**
     * Plays the next song on the server.
     */
    fun playNextSongOnServer() {
        if (_isConnected.value) {
            client.sendNextCommand()
        }
    }

    /**
     * Plays the previous song on the server.
     */
    fun playPreviousSongOnServer() {
        if (_isConnected.value) {
            client.sendPreviousCommand()
        }
    }

    /**
     * Processes a player update from the server.
     * @param update The update message from the server.
     */
    private fun processPlayerUpdate(update: String) {
        try {
            // Parse the update message
            val updateData = Json.decodeFromString<JsonObject>(update)

            // Handle different types of updates
            when (val type = updateData["type"]?.jsonPrimitive?.content) {
                "nowPlaying" -> {
                    // Update the current song
                    val songJson = updateData["song"]?.jsonPrimitive?.content
                    if (songJson != null) {
                        val song = Json.decodeFromString<Song>(songJson)
                        PlayerService.onPlaySongCommand(song)
                    }
                }

                "playbackState" -> {
                    // Update the playback state
                    val isPlaying = updateData["isPlaying"]?.jsonPrimitive?.content?.toBoolean()
                    if (isPlaying != null) {
                        PlayerService.onIsPlayingChanged(isPlaying)
                    }
                }

                else -> {
                    Logger.d { "Received unknown player update type: $type" }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error processing player update: $update" }
        }
    }

    /**
     * Sends a player update to all connected clients.
     * @param update The update message to send.
     */
    fun sendPlayerUpdate(update: String) {
        server.sendPlayerUpdate(update)
    }
}
