package dr.ulysses.network

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
     * Sends a player update to all connected clients.
     * @param update The update object to send.
     */
    fun sendPlayerUpdate(update: PlayerUpdate)
}
