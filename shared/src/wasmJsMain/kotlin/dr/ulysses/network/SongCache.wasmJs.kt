package dr.ulysses.network

// The browser can't store song files, so the web app only remote-controls a server
internal actual class SongCache {
    actual suspend fun pathFor(
        serverPath: String,
        download: suspend () -> ByteArray,
    ): String? = null

    actual suspend fun retain(serverPaths: Set<String>) = Unit
}
