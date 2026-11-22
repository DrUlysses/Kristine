package dr.ulysses.entities

// The browser has no access to the song files
actual suspend fun musicFolder(): String? = null

actual suspend fun moveSongFile(
    from: String,
    to: String,
): Boolean = false

actual suspend fun writeSongFile(
    file: String,
    bytes: ByteArray,
): String? = null
