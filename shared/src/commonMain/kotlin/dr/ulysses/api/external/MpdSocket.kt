package dr.ulysses.api.external

/**
 * One connection to an MPD server, speaking its line protocol; browsers can't open one.
 */
internal expect class MpdSocket {
    /**
     * Sends [line] and returns the answer's key/value pairs; an `ACK` answer throws [MpdException].
     */
    suspend fun command(line: String): List<Pair<String, String>>

    /**
     * Waits for the changes of the `idle` command [line]; unlike [command] it can be canceled, which closes this
     * connection anyway.
     */
    suspend fun idle(line: String): List<Pair<String, String>>

    /**
     * Like [command], for a command that answers with one chunk of binary data (`albumart`, `readpicture`).
     */
    suspend fun binary(line: String): Pair<List<Pair<String, String>>, ByteArray>

    fun close()
}

/**
 * Opens a connection to the MPD server at [address]:[port] and reads its greeting.
 */
internal expect suspend fun openMpdSocket(
    address: String,
    port: Int,
): MpdSocket

/**
 * The server refused a command, e.g. a wrong password or an unknown song.
 */
internal class MpdException(message: String) : Exception(message)
