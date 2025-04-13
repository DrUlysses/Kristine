package dr.ulysses.network

import dr.ulysses.entities.Song

/**
 * Server that broadcasts its presence on the local network using UDP.
 */
expect class NetworkServer() {
    companion object {
        val BROADCAST_MESSAGE_PREFIX: String
        val BROADCAST_INTERVAL_SECONDS: Int
        val DISCOVERY_PORT: Int
    }

    /**
     * Starts broadcasting UDP packets on the local network.
     * @return The port number the server is listening on
     */
    suspend fun start(): Int

    /**
     * Stops the UDP broadcast server.
     */
    fun stop()

    /**
     * Starts the WebSocket server for real-time communication.
     * @param onPlaySongCommand Callback that will be called when a client sends a play song command.
     * @param onPauseCommand Callback that will be called when a client sends a pause command.
     * @param onResumeCommand Callback that will be called when a client sends a resume command.
     * @param onNextCommand Callback that will be called when a client sends a next command.
     * @param onPreviousCommand Callback that will be called when a client sends a previous command.
     */
    fun startWebSocketServer(
        onPlaySongCommand: (Song) -> Unit,
        onPauseCommand: () -> Unit,
        onResumeCommand: () -> Unit,
        onNextCommand: () -> Unit,
        onPreviousCommand: () -> Unit,
    )

    /**
     * Stops the WebSocket server.
     */
    fun stopWebSocketServer()

    /**
     * Sends a player update to all connected clients.
     * @param update The update message to send.
     */
    fun sendPlayerUpdate(update: String)
}
