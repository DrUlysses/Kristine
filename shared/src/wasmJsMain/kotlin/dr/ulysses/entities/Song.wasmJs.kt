package dr.ulysses.entities

// The browser has no music folder to scan; it remote-controls a server, whose window it shows
actual suspend fun refreshSongs(): List<Song> = SongRepository.getAllSongs()

actual fun fileExists(path: String): Boolean = false

actual fun getDurationFromMetadata(path: String): Int? = null
