package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.SongRepository.getAllSongs
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Server that broadcasts its presence on the local network using UDP.
 */
class NetworkServer {
    companion object {
        const val BROADCAST_MESSAGE_PREFIX = "Kristine Server Discovery:"
        const val BROADCAST_INTERVAL_SECONDS = 5
        const val DISCOVERY_PORT = 45678 // Port used only for initial discovery
    }

    private var broadcastJob: Job? = null
    private var httpServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var serverPort: Int = 0

    /**
     * Starts broadcasting UDP packets on the local network.
     * @return The port number the server is listening on
     */
    suspend fun start(): Int {
        if (broadcastJob != null) return serverPort

        Logger.d { "Starting UDP broadcast server" }

        // Create a socket and get the port synchronously
        val selectorManager = SelectorManager(Dispatchers.Default)
        val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", 0)) {
            broadcast = true
        }

        // Get the dynamically assigned port
        val localAddress = socket.localAddress as InetSocketAddress
        serverPort = localAddress.port
        Logger.d { "Server bound to port: $serverPort" }

        // Start the HTTP server
        startHttpServer()

        // Start broadcasting in a separate coroutine
        broadcastJob = scope.launch {
            try {
                while (isActive) {
                    // Broadcast to localhost
                    sendBroadcast(socket, "127.0.0.1")

                    // Broadcast to 192.168.x.255 (common broadcast addresses)
                    for (subnet in 0..10) {
                        sendBroadcast(socket, "192.168.$subnet.255")
                    }

                    delay(BROADCAST_INTERVAL_SECONDS.seconds)
                }
            } catch (e: Exception) {
                Logger.e(e) { "Error in UDP broadcast server" }
            } finally {
                socket.close()
                selectorManager.close()
            }
        }

        return serverPort
    }

    private suspend fun sendBroadcast(socket: BoundDatagramSocket, address: String) {
        try {
            // Create a message with port information
            val broadcastMessage = "$BROADCAST_MESSAGE_PREFIX$serverPort"
            Logger.d { "Broadcasting to $address: $broadcastMessage" }

            socket.send(
                Datagram(
                    packet = ByteReadPacket(broadcastMessage.toByteArray()),
                    address = InetSocketAddress(address, DISCOVERY_PORT)
                )
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to broadcast to $address" }
        }
    }

    /**
     * Starts the HTTP server for handling REST API requests.
     */
    private fun startHttpServer() {
        if (httpServer != null) return

        Logger.d { "Starting HTTP server on port: $serverPort" }

        httpServer = embeddedServer(
            factory = CIO,
            port = serverPort
        ) {
            routing {
                get("/songs") {
                    // Launch a coroutine to call the suspend function
                    call.respond(HttpStatusCode.OK, Json.encodeToString(getAllSongs()))
                }
            }
        }.start(wait = false)

        Logger.d { "HTTP server started on port: $serverPort" }
    }

    /**
     * Stops the UDP broadcast server.
     */
    fun stop() {
        broadcastJob?.cancel()
        broadcastJob = null

        httpServer?.stop(1000, 2000)
        httpServer = null

        Logger.d { "Stopped UDP broadcast server and HTTP server" }
    }
}
