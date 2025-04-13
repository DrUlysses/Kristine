package dr.ulysses.network

import io.ktor.http.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Server that listens for discovery requests from other instances of the app.
 */
class NetworkServer {
    companion object {
        const val SERVER_PORT = 8765
        const val DISCOVERY_ENDPOINT = "/discover"
    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Starts the server on the local network.
     */
    fun start() {
        if (server != null) return

        scope.launch {
            server = embeddedServer(
                factory = CIO,
                // Use 0.0.0.0 to listen on all network interfaces
                host = "0.0.0.0",
                port = SERVER_PORT
            ) {
                routing {
                    // Endpoint for discovery
                    get(DISCOVERY_ENDPOINT) {
                        call.respond(HttpStatusCode.OK, "Kristine Server")
                    }
                }
            }
            server?.start(wait = false)
        }
    }

    /**
     * Stops the server.
     */
    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
