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

    // StateFlow to hold the list of discovered servers
    private val _discoveredServers = MutableStateFlow<List<String>>(emptyList())
    val discoveredServers: StateFlow<List<String>> = _discoveredServers.asStateFlow()

    /**
     * Starts the server and client for network discovery.
     */
    fun start() {
        scope.launch {
            server.start()
            client.startDiscovery { servers ->
                _discoveredServers.value = servers
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
}
