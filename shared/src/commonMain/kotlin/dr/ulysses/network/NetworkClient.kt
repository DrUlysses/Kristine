package dr.ulysses.network

import dr.ulysses.Logger
import dr.ulysses.api.internal.LibraryRoutes
import dr.ulysses.api.internal.LibraryTasks
import dr.ulysses.api.internal.TaskCommand
import dr.ulysses.api.internal.TaskUpdate
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class NetworkClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Every read and write of `servers` runs here, so the map needs no locking
    private val discoveryContext = Dispatchers.Default.limitedParallelism(1)
    private val servers = mutableMapOf<String, Seen>()
    private var discoveryJob: Job? = null

    private var connectionJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null
    private var tasksSession: DefaultClientWebSocketSession? = null

    // Each connection has its own client, because the client holds that server's pinned certificate
    private var connectionClient: HttpClient? = null
    private var serverUrl = ""

    private data class Seen(
        val port: Int,
        val at: Long,
    )

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
    fun addCustomServer(
        address: String,
        port: Int,
        onServersChanged: (Map<String, Int>) -> Unit,
    ) {
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

    /**
     * Connects over TLS, accepting only [pinnedFingerprint] (any certificate when null). [onCertificate] gets the
     * fingerprint of a connected server, [onCertificateChanged] the one a server presented instead of the pinned one.
     */
    fun connect(
        address: String,
        port: Int,
        pinnedFingerprint: String?,
        onPlayerUpdate: (PlayerUpdate) -> Unit,
        onConnectionChanged: (Boolean) -> Unit,
        onCertificate: suspend (String) -> Unit,
        onCertificateChanged: (String) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        if (connectionJob?.isActive == true) {
            return
        }
        connectionJob = scope.launch {
            var presented: String? = null
            val client = pinnedHttpClient(
                pinnedFingerprint = pinnedFingerprint,
                onCertificate = { presented = it }
            ) {
                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(Json)
                }
            }
            connectionClient = client
            serverUrl = "https://$address:$port"
            try {
                client.wss(
                    host = address,
                    port = port,
                    path = "/player"
                ) {
                    session = this
                    presented?.let { onCertificate(it) }
                    onConnectionChanged(true)
                    // Long library tasks have their own socket; it closes with this one
                    launch {
                        try {
                            client.wss(
                                host = address,
                                port = port,
                                path = LibraryRoutes.TASKS_ROUTE
                            ) {
                                tasksSession = this
                                for (frame in incoming) {
                                    if (frame !is Frame.Text) {
                                        continue
                                    }
                                    runCatching { Json.decodeFromString<TaskUpdate>(frame.readText()) }
                                        .onSuccess(LibraryTasks::onUpdate)
                                        .onFailure { Logger.e(it) { "Ignoring malformed task update" } }
                                }
                            }
                        } catch (e: Exception) {
                            currentCoroutineContext().ensureActive()
                            Logger.e(e) { "Task connection to $address:$port failed" }
                        } finally {
                            tasksSession = null
                        }
                    }
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
                val changed = presented?.takeIf { pinnedFingerprint != null && it != pinnedFingerprint }
                if (changed != null) {
                    onCertificateChanged(changed)
                } else {
                    Logger.e(e) { "WebSocket connection to $address:$port failed" }
                    onFailed(e.message ?: e::class.simpleName.orEmpty())
                }
            } finally {
                // Also runs when the server closes the socket, so the app falls back to local playback
                session = null
                connectionClient = null
                client.close()
                onConnectionChanged(false)
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }

    /** [disconnect], returning once the connection has ended. */
    suspend fun disconnectAndWait() {
        connectionJob?.cancelAndJoin()
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

    internal fun sendTask(command: TaskCommand) {
        val session = tasksSession ?: return
        scope.launch {
            try {
                session.send(Frame.Text(Json.encodeToString(command)))
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Logger.e(e) { "Sending ${command::class.simpleName} failed" }
            }
        }
    }

    /**
     * A call to the connected server's library API: JSON [body] in, JSON out.
     */
    internal suspend fun callLibrary(
        route: String,
        body: String,
    ): String {
        val client = checkNotNull(connectionClient) { "Not connected" }
        val response = client.post("$serverUrl$route") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        check(response.status.isSuccess()) { "$route: ${response.status} ${response.bodyAsText()}" }
        return response.bodyAsText()
    }

    /** Null when the server has no artwork for [path]. */
    suspend fun fetchArtwork(path: String): ByteArray? = get(
        route = ARTWORK_ROUTE,
        path = path
    ).takeIf { it.status.isSuccess() }?.bodyAsBytes()

    /** A whole song file of the server's current window. */
    suspend fun downloadSong(path: String): ByteArray {
        val response = get(
            route = SONG_ROUTE,
            path = path
        )
        check(response.status.isSuccess()) { "Download of $path: ${response.status}" }
        return response.bodyAsBytes()
    }

    /** A request to the connected server, about its song at [path] when given. */
    private suspend fun get(
        route: String,
        path: String? = null,
    ): HttpResponse {
        val client = checkNotNull(connectionClient) { "Not connected" }
        return client.get("$serverUrl$route") {
            path?.let {
                parameter(
                    key = "path",
                    value = it
                )
            }
        }
    }

    private companion object {
        val SERVER_TIMEOUT = 15.seconds
    }
}
