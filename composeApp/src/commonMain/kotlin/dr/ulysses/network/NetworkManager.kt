package dr.ulysses.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /**
     * Starts the server and client for network discovery.
     */
    fun start() {
        scope.launch {
            // Start the server and get the dynamically assigned port
            val port = server.start()
            _localServerPort.value = port

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
        client.stopDiscovery()
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
            }
        }
    }
}
