package dr.ulysses.network

import dr.ulysses.Logger
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * Accepts TLS connections on [port] on every interface and relays the decrypted bytes to the plain server
 * listening on loopback at [backendPort].
 *
 * Ktor's CIO server can't serve TLS, and Netty, which can, needs Android 8; the platform's TLS server socket
 * works on every Android version.
 */
// shortcut: javax.net.ssl and java.net sockets, because no Kotlin library serves TLS on Android below 8
internal class TlsFront(
    keyStore: KeyStore,
    password: CharArray,
    port: Int,
    private val backendPort: Int,
) {
    private val serverSocket: ServerSocket = SSLContext.getInstance("TLS").apply {
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(
                keyStore,
                password
            )
        }.keyManagers
        init(
            keyManagers,
            null,
            null
        )
    }.serverSocketFactory.createServerSocket(port)

    /** The port actually listened on; differs from the requested one when that was 0. */
    val port: Int get() = serverSocket.localPort

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        try {
            while (isActive) {
                val client = serverSocket.accept()
                launch { relay(client) }
            }
        } catch (e: Exception) {
            // Closing the socket in stop() ends accept() with an exception
            if (!serverSocket.isClosed) {
                Logger.e(e) { "TLS front stopped" }
            }
        }
    }

    fun stop() = serverSocket.close()

    private suspend fun relay(client: Socket) = withContext(Dispatchers.IO) {
        try {
            client.use {
                Socket(
                    InetAddress.getLoopbackAddress(),
                    backendPort
                ).use { backend ->
                    // Blocking copies; whichever side closes first closes both, which ends the other copy
                    val toBackend = launch {
                        runCatching { client.getInputStream().copyTo(backend.getOutputStream()) }
                        backend.close()
                    }
                    runCatching { backend.getInputStream().copyTo(client.getOutputStream()) }
                    client.close()
                    toBackend.join()
                }
            }
        } catch (e: Exception) {
            // A failed handshake (e.g. a browser that doesn't accept the certificate yet) only drops this client
            Logger.d(e) { "TLS connection dropped" }
        }
    }
}
