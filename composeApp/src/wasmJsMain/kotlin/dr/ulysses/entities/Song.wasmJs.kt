package dr.ulysses.entities

import dr.ulysses.Logger
import dr.ulysses.network.NetworkManager.currentServer
import dr.ulysses.network.NetworkManager.fetchSongsFromCurrentServer

actual suspend fun refreshSongs(): List<Song> {
    // Check if connected to a server
    if (currentServer.value != null) {
        // Try to fetch songs from the server
        val serverSongs = fetchSongsFromCurrentServer()
        if (serverSongs != null) {
            // If server songs were successfully fetched, return them
            Logger.d { "Fetched ${serverSongs.size} songs from server in WASM" }
            return serverSongs
        }
        // If server songs couldn't be fetched, fall back to an empty list
        Logger.d { "Failed to fetch songs from server in WASM" }
    } else {
        Logger.d { "Not connected to a server in WASM" }
    }

    // Not connected to a server or failed to fetch server songs
    // Return empty list since a file system is not available in WASM
    return emptyList()
}

/**
 * Stub implementation for checking if a file exists in WASM JS
 * Always returns false
 */
actual fun fileExists(path: String): Boolean {
    Logger.d { "WASM JS: fileExists not implemented, path was: $path" }
    return false
}

/**
 * Gets the duration in seconds from the metadata of a file.
 * @param path The path to the file
 * @return The duration in seconds, or null if it couldn't be determined
 *
 * In WASM JS, this is a stub implementation that always returns null
 */
actual fun getDurationFromMetadata(path: String): Int? {
    Logger.d { "WASM JS: getDurationFromMetadata not implemented, path was: $path" }
    return null
}
