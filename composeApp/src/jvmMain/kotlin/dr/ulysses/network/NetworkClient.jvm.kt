package dr.ulysses.network

import androidx.compose.runtime.MutableState
import dr.ulysses.Logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun startDiscovery(
    discoveryJob: MutableState<Job?>,
    scope: CoroutineScope,
    discoveredServers: MutableMap<String, Int>,
    serverLastSeen: MutableMap<String, Long>,
    cleanupStaleServers: () -> Unit,
    onServersDiscovered: (Map<String, Int>) -> Unit,
) {
    if (discoveryJob.value != null) return

    Logger.d { "Starting UDP server discovery process" }
    discoveryJob.value = scope.launch {
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
                // Extract an IP address from the socket address string
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

                            // Update the last-seen timestamp
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
