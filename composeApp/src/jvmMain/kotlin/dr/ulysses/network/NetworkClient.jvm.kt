package dr.ulysses.network

import dr.ulysses.Logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * JVM-specific implementation of NetworkClient.
 * This is the full implementation that uses UDP sockets for discovery.
 */
actual class NetworkClient {
    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val discoveredServers = mutableMapOf<String, Int>() // Map of IP address to port
    private val serverTimeout = 15.seconds.inWholeMilliseconds

    // Map to track when servers were last seen
    private val serverLastSeen = mutableMapOf<String, Long>()

    /**
     * Starts the discovery process to find servers on the local network.
     * @param onServersDiscovered Callback that will be called with the map of discovered servers (IP to port).
     */
    actual fun startDiscovery(onServersDiscovered: (Map<String, Int>) -> Unit) {
        if (discoveryJob != null) return

        Logger.d { "Starting UDP server discovery process" }
        discoveryJob = scope.launch {
            val selectorManager = SelectorManager(Dispatchers.Default)
            val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", NetworkServer.DISCOVERY_PORT))

            try {
                // Start a periodic job to clean up stale servers
                val cleanupJob = launch {
                    while (isActive) {
                        cleanupStaleServers()
                        onServersDiscovered(discoveredServers)
                        delay(5.seconds)
                    }
                }

                // Listen for incoming UDP broadcasts
                while (isActive) {
                    val datagram = socket.receive()
                    val message = datagram.packet.readText()
                    // Extract IP address from the socket address string
                    val addressStr = datagram.address.toString()
                    val senderAddress = addressStr.substringBefore(':').replace("/", "")

                    // Check if the message starts with the broadcast prefix
                    if (message.startsWith(NetworkServer.BROADCAST_MESSAGE_PREFIX)) {
                        val currentTime = Clock.System.now().toEpochMilliseconds()

                        // Extract the port from the message
                        val serverPort =
                            message.substring(NetworkServer.BROADCAST_MESSAGE_PREFIX.length).toIntOrNull() ?: 0

                        if (serverPort > 0) {
                            // Use the actual sender IP instead of the broadcast address
                            if (senderAddress != "0.0.0.0" && senderAddress != "255.255.255.255") {
                                Logger.d { "Server discovered at: $senderAddress:$serverPort" }

                                // Store the server with its port
                                discoveredServers[senderAddress] = serverPort

                                // Update the last seen timestamp
                                serverLastSeen[senderAddress] = currentTime

                                // Notify about the updated server list
                                onServersDiscovered(discoveredServers)
                            }
                        }
                    }
                }

                cleanupJob.cancel()
            } catch (e: Exception) {
                Logger.e(e) { "Error in UDP discovery client" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }
    }

    /**
     * Remove servers that haven't been seen recently
     */
    private fun cleanupStaleServers() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        val staleServers = serverLastSeen.filter { (_, lastSeen) ->
            (currentTime - lastSeen) > serverTimeout
        }.keys

        if (staleServers.isNotEmpty()) {
            Logger.d { "Removing stale servers: $staleServers" }
            // Remove each stale server from the discoveredServers map
            staleServers.forEach {
                discoveredServers.remove(it)
                serverLastSeen.remove(it)
            }
        }
    }

    /**
     * Stops the discovery process.
     */
    actual fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        discoveredServers.clear()
        serverLastSeen.clear()
        Logger.d { "Stopped UDP server discovery process" }
    }

    /**
     * Connects to a custom server with the given address and port.
     * @param address The IP address of the server.
     * @param port The port number of the server.
     * @param onServersDiscovered Callback that will be called with the updated map of discovered servers.
     */
    actual fun connectToCustomServer(address: String, port: Int, onServersDiscovered: (Map<String, Int>) -> Unit) {
        Logger.d { "Connecting to custom server at $address:$port" }

        // Add the custom server to the discoveredServers map
        discoveredServers[address] = port

        // Update the last seen timestamp
        serverLastSeen[address] = Clock.System.now().toEpochMilliseconds()

        // Notify about the updated server list
        onServersDiscovered(discoveredServers)
    }
}
