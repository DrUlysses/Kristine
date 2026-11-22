package dr.ulysses.entities

actual suspend fun localLyrics(path: String): String? = null

actual suspend fun saveLyricsFile(
    path: String,
    lyrics: String,
) {
}
