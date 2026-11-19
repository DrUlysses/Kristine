package dr.ulysses.entities

actual val canDownloadSongs = false

actual suspend fun downloadAudio(
    query: String,
    fileName: String,
): String =
    throw UnsupportedOperationException("Not yet on Android")
