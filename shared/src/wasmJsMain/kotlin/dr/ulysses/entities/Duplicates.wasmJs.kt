package dr.ulysses.entities

// The browser has no access to the song files
actual suspend fun deleteSongFile(path: String): Boolean = false
