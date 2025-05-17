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
 * WasmJs implementation of fileExists.
 * In a browser environment, we can't directly check if a file exists on the file system.
 * This implementation always returns true since we don't expect to have local file paths in a web app.
 */
actual fun fileExists(path: String): Boolean = true
