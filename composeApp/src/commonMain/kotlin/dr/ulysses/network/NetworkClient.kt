package dr.ulysses.network

import co.touchlab.kermit.Logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Client that discovers other instances of the app on the network using UDP.
 */
class NetworkClient {
    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val discoveredServers = mutableSetOf<String>()
    private val serverTimeout = 15.seconds.inWholeMilliseconds

    // Map to track when servers were last seen
    private val serverLastSeen = mutableMapOf<String, Long>()

    /**
     * Starts the discovery process to find servers on the local network.
     * @param onServersDiscovered Callback that will be called with the list of discovered servers.
     */
    fun startDiscovery(onServersDiscovered: (List<String>) -> Unit) {
        if (discoveryJob != null) return

        Logger.d { "Starting UDP server discovery process" }
        discoveryJob = scope.launch {
            val selectorManager = SelectorManager(Dispatchers.Default)
            val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", NetworkServer.SERVER_PORT))

            try {
                // Start a periodic job to clean up stale servers
                val cleanupJob = launch {
                    while (isActive) {
                        cleanupStaleServers()
                        onServersDiscovered(discoveredServers.toList())
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

                    if (message == NetworkServer.BROADCAST_MESSAGE) {
                        val currentTime = Clock.System.now().toEpochMilliseconds()

                        // Use the actual sender IP instead of the broadcast address
                        if (senderAddress != "0.0.0.0" && senderAddress != "255.255.255.255") {
                            if (!discoveredServers.contains(senderAddress)) {
                                Logger.d { "Server discovered at: $senderAddress" }
                                discoveredServers.add(senderAddress)
                            }

                            // Update the last seen timestamp
                            serverLastSeen[senderAddress] = currentTime

                            // Notify about the updated server list
                            onServersDiscovered(discoveredServers.toList())
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
            discoveredServers.removeAll(staleServers)
            staleServers.forEach { serverLastSeen.remove(it) }
        }
    }

    /**
     * Stops the discovery process.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        discoveredServers.clear()
        serverLastSeen.clear()
        Logger.d { "Stopped UDP server discovery process" }
    }
}
