package dr.ulysses.network

import dr.ulysses.Logger

/**
 * WASM-specific implementation of NetworkServer.
 * Since UDP sockets and HTTP servers might not be fully supported in WASM,
 * this is a simplified implementation that provides the necessary functionality.
 */
actual class NetworkServer {
    actual companion object {
        actual val BROADCAST_MESSAGE_PREFIX = "Kristine Server Discovery:"
        actual val BROADCAST_INTERVAL_SECONDS = 5
        actual val DISCOVERY_PORT = 45678 // Port used only for initial discovery
    }

    private var serverPort: Int = 8080 // Default port for WASM

    /**
     * Starts the server.
     * In WASM, we don't actually start a server, but we return a port for compatibility.
     * @return The port number the server would be listening on
     */
    actual suspend fun start(): Int {
        Logger.d { "Starting NetworkServer in WASM (stub implementation)" }
        return serverPort
    }

    /**
     * Stops the server.
     * In WASM, this is a no-op.
     */
    actual fun stop() {
        Logger.d { "Stopping NetworkServer in WASM (stub implementation)" }
    }

    /**
     * Sends a player update to all connected clients.
     * In WASM, this is a stub implementation that just logs the update.
     * @param update The update object to send.
     */
    actual fun sendPlayerUpdate(update: PlayerUpdate) {
        Logger.d { "Player update in WASM (stub implementation): ${update.updateType.name}" }
    }
}
