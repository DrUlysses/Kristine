package dr.ulysses.network

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

/**
 * Client that discovers other instances of the app on the network.
 */
class NetworkClient {
    private val client = HttpClient {
        // Configure client with a short timeout for discovery
        expectSuccess = false
    }
    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Starts the discovery process to find servers on the local network.
     * @param onServersDiscovered Callback that will be called with the list of discovered servers.
     */
    fun startDiscovery(onServersDiscovered: (List<String>) -> Unit) {
        if (discoveryJob != null) return

        Logger.d { "Starting server discovery process" }
        discoveryJob = scope.launch {
            while (isActive) {
                val servers = mutableListOf<String>()

                // Scan local network (focus on common subnet masks)
                Logger.d { "Starting network scan for servers..." }

                // Only scan the most common subnets (192.168.0.x and 192.168.10.x) instead of all 192.168.x.x
                for (subnet in 0..10) {
                    for (host in 1..254) {
                        val ip = "192.168.$subnet.$host"
                        // Only log every 10th IP to reduce log spam
                        if (host % 10 == 0) {
                            Logger.d { "Checking IP: $ip" }
                        }
                        try {
                            val response =
                                client.get("http://$ip:${NetworkServer.SERVER_PORT}${NetworkServer.DISCOVERY_ENDPOINT}")
                            if (response.status == HttpStatusCode.OK) {
                                Logger.d { "Server found at IP: $ip" }
                                servers.add(ip)
                            }
                        } catch (_: Exception) {
                            // Ignore connection errors - this is expected for most IPs
                        }
                    }
                }
                Logger.d { "Network scan completed. Found ${servers.size} servers." }

                // Also try to discover on localhost for testing
                Logger.d { "Checking localhost" }
                try {
                    val response =
                        client.get("http://localhost:${NetworkServer.SERVER_PORT}${NetworkServer.DISCOVERY_ENDPOINT}")
                    if (response.status == HttpStatusCode.OK) {
                        Logger.d { "Server found at localhost" }
                        servers.add("localhost")
                    }
                } catch (_: Exception) {
                    // Ignore connection errors
                }

                onServersDiscovered(servers)

                // Wait before the next scan
                delay(10.seconds.inWholeMilliseconds)
            }
        }
    }

    /**
     * Stops the discovery process.
     */
    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        Logger.d { "Stopped server discovery process" }
    }
}
