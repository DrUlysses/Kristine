package dr.ulysses.network

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*

/**
 * Client that discovers other instances of the app on the network.
 */
class NetworkClient {
    private val client = HttpClient {
        // Configure client with a short timeout for discovery
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

                // Scan local network (192.168.x.x)
                Logger.d { "Starting network scan for servers..." }
                for (i in 1..254) {
                    for (j in 1..254) {
                        val ip = "192.168.$i.$j"
                        Logger.d { "Checking IP: $ip" }
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
                delay(5000) // 5 seconds between scans
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
