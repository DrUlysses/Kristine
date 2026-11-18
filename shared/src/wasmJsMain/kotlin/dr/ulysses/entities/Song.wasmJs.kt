package dr.ulysses.entities

import dr.ulysses.network.NetworkManager

// The browser has no file system: the library is whatever the connected server has
actual suspend fun refreshSongs(): List<Song> = NetworkManager.fetchSongsFromCurrentServer().orEmpty()

actual fun fileExists(path: String): Boolean = false

actual fun getDurationFromMetadata(path: String): Int? = null
