package dr.ulysses.network

import dr.ulysses.Logger
import kotlinx.datetime.Clock

/**
 * WASM-specific implementation of NetworkClient.
 * Since UDP sockets might not be fully supported in WASM,
 * this is a simplified implementation that provides the necessary functionality.
 */
actual class NetworkClient {
    private val discoveredServers = mutableMapOf<String, Int>() // Map of IP address to port
    private val serverLastSeen = mutableMapOf<String, Long>()

    /**
     * Starts the discovery process.
     * In WASM, we don't actually discover servers, but we provide the callback for compatibility.
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
}
