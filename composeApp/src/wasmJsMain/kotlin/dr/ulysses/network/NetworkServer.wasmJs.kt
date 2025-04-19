package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song

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

    // Player control callbacks (stub implementations)
    private var onPlaySongCommandCallback: ((Song) -> Unit)? = null
    private var onPauseCommandCallback: (() -> Unit)? = null
    private var onResumeCommandCallback: (() -> Unit)? = null
    private var onNextCommandCallback: (() -> Unit)? = null
    private var onPreviousCommandCallback: (() -> Unit)? = null

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
     * Starts the WebSocket server for real-time communication.
     * In WASM, this is a stub implementation that just stores the callbacks.
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

        Logger.d { "Player control callbacks registered in WASM (stub implementation)" }
    }

    /**
     * Stops the WebSocket server.
     * In WASM, this is a stub implementation that just clears the callbacks.
     */
    actual fun stopWebSocketServer() {
        // Clear the callbacks
        onPlaySongCommandCallback = null
        onPauseCommandCallback = null
        onResumeCommandCallback = null
        onNextCommandCallback = null
        onPreviousCommandCallback = null

        Logger.d { "Player control callbacks cleared in WASM (stub implementation)" }
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
