package dr.ulysses.api.external

// A browser can't open plain TCP connections, which MPD needs
internal actual class MpdSocket {
    actual suspend fun command(line: String): List<Pair<String, String>> = unsupported()

    actual suspend fun idle(line: String): List<Pair<String, String>> = unsupported()

    actual suspend fun binary(line: String): Pair<List<Pair<String, String>>, ByteArray> = unsupported()

    actual fun close() = Unit
}

internal actual suspend fun openMpdSocket(
    address: String,
    port: Int,
): MpdSocket = unsupported()

private fun unsupported(): Nothing = throw MpdException("The browser can't connect to an MPD server")
