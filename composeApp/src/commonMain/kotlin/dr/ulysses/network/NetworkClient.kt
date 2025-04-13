package dr.ulysses.network

import dr.ulysses.entities.Song

/**
 * Client that discovers other instances of the app on the network using UDP.
 */
expect class NetworkClient() {
    /**
     * Starts the discovery process to find servers on the local network.
     * @param onServersDiscovered Callback that will be called with the map of discovered servers (IP to port).
     */
    fun startDiscovery(onServersDiscovered: (Map<String, Int>) -> Unit)

    /**
     * Stops the discovery process.
     */
    fun stopDiscovery()

    /**
     * Connects to a custom server with the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onServersDiscovered Callback that will be called with the updated map of discovered servers.
     */
    fun connectToCustomServer(address: String, port: Int, onServersDiscovered: (Map<String, Int>) -> Unit)

    /**
     * Connects to the WebSocket server at the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onPlayerUpdate Callback that will be called when the player state is updated.
     * @param onConnectionStateChange Callback that will be called when the connection state changes.
     */
    fun connectToWebSocket(
        address: String,
        port: Int,
        onPlayerUpdate: (String) -> Unit,
        onConnectionStateChange: (Boolean) -> Unit,
    )

    /**
     * Disconnects from the WebSocket server.
     */
    fun disconnectFromWebSocket()

    /**
     * Sends a command to play a song on the server.
     * @param song The song to play.
     */
    fun sendPlaySongCommand(song: Song)

    /**
     * Sends a command to pause playback on the server.
     */
    fun sendPauseCommand()

    /**
     * Sends a command to resume playback on the server.
     */
    fun sendResumeCommand()

    /**
     * Sends a command to play the next song on the server.
     */
    fun sendNextCommand()

    /**
     * Sends a command to play the previous song on the server.
     */
    fun sendPreviousCommand()
}
