package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.entities.Song
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class NetworkClient {
    private val httpClient = HttpClient {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Every read and write of `servers` runs here, so the map needs no locking
    private val discoveryContext = Dispatchers.Default.limitedParallelism(1)
    private val servers = mutableMapOf<String, Seen>()
    private var discoveryJob: Job? = null

    private var connectionJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null

    private class Seen(val port: Int, val at: Long)

    fun startDiscovery(onServersChanged: (Map<String, Int>) -> Unit) {
        if (discoveryJob != null) {
            return
        }
        discoveryJob = scope.launch(discoveryContext) {
            launch {
                while (isActive) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    servers.values.removeAll { now - it.at > SERVER_TIMEOUT.inWholeMilliseconds }
                    onServersChanged(snapshot())
                    delay(BROADCAST_INTERVAL)
                }
            }
            serverAnnouncements()
                .catch { Logger.e(it) { "UDP discovery stopped" } }
                .collect { (address, port) ->
                    servers[address] = Seen(
                        port = port,
                        at = Clock.System.now().toEpochMilliseconds()
                    )
                    onServersChanged(snapshot())
                }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        scope.launch(discoveryContext) { servers.clear() }
    }

    /** A server typed in by hand never announces itself, so it never goes stale. */
    fun addCustomServer(address: String, port: Int, onServersChanged: (Map<String, Int>) -> Unit) {
        scope.launch(discoveryContext) {
            servers[address] = Seen(
                port = port,
                at = Long.MAX_VALUE
            )
            onServersChanged(snapshot())
        }
    }

    // A copy, because StateFlow ignores a value equal to the previous one
    private fun snapshot() = servers.mapValues { it.value.port }

    fun connect(
        address: String,
        port: Int,
        onPlayerUpdate: (PlayerUpdate) -> Unit,
        onConnectionChanged: (Boolean) -> Unit,
    ) {
        if (connectionJob?.isActive == true) {
            return
        }
        connectionJob = scope.launch {
            try {
                httpClient.webSocket(
                    host = address,
                    port = port,
                    path = "/player"
                ) {
                    session = this
                    onConnectionChanged(true)
                    for (frame in incoming) {
                        if (frame !is Frame.Text) {
                            continue
                        }
                        runCatching { Json.decodeFromString<PlayerUpdate>(frame.readText()) }
                            .onSuccess(onPlayerUpdate)
                            .onFailure { Logger.e(it) { "Ignoring malformed player update" } }
                    }
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Logger.e(e) { "WebSocket connection to $address:$port failed" }
            } finally {
                // Also runs when the server closes the socket, so the app falls back to local playback
                session = null
                onConnectionChanged(false)
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }

    fun send(command: WebSocketCommand) {
        val session = session ?: return
        scope.launch {
            try {
                session.sendSerialized<WebSocketCommand>(command)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Logger.e(e) { "Sending ${command::class.simpleName} failed" }
            }
        }
    }

    suspend fun fetchSongs(address: String, port: Int): List<Song> =
        Json.decodeFromString(httpClient.get("http://$address:$port/songs").bodyAsText())

    private companion object {
        val SERVER_TIMEOUT = 15.seconds
    }
}
