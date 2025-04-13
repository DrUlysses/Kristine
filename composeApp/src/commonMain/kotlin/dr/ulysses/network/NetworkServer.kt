package dr.ulysses.network

import co.touchlab.kermit.Logger
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

/**
 * Server that broadcasts its presence on the local network using UDP.
 */
class NetworkServer {
    companion object {
        const val SERVER_PORT = 54321
        const val BROADCAST_MESSAGE = "Kristine Server Discovery"
        const val BROADCAST_INTERVAL_SECONDS = 5
    }

    private var broadcastJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Starts broadcasting UDP packets on the local network.
     */
    fun start() {
        if (broadcastJob != null) return

        Logger.d { "Starting UDP broadcast server" }
        broadcastJob = scope.launch {
            val selectorManager = SelectorManager(Dispatchers.Default)
            val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", SERVER_PORT))

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
    }

    private suspend fun sendBroadcast(socket: BoundDatagramSocket, address: String) {
        try {
            Logger.d { "Broadcasting to $address" }
            socket.send(
                Datagram(
                    packet = ByteReadPacket(BROADCAST_MESSAGE.toByteArray()),
                    address = InetSocketAddress(address, SERVER_PORT)
                )
            )
        } catch (e: Exception) {
            Logger.e(e) { "Failed to broadcast to $address" }
        }
    }

    /**
     * Stops the UDP broadcast server.
     */
    fun stop() {
        broadcastJob?.cancel()
        broadcastJob = null
        Logger.d { "Stopped UDP broadcast server" }
    }
}
