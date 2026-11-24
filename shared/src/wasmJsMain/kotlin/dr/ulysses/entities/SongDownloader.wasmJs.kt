package dr.ulysses.entities

// A browser can't run yt-dlp, and has no music folder to download into
actual val canDownloadSongs = false

actual suspend fun downloadAudio(
    query: String,
    fileName: String,
): String =
    throw UnsupportedOperationException("Downloading songs isn't possible in the browser")

actual suspend fun youTubeVideos(link: String): List<YouTubeVideo> =
    throw UnsupportedOperationException("Downloading songs isn't possible in the browser")
