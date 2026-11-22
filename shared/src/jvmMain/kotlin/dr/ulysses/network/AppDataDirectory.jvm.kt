package dr.ulysses.network

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Kristine's own folder (database, certificate, default music folder):
 * KRISTINE_HOME, else the user's data folder.
 */
internal actual fun appDataDirectory(): String {
    val home = System.getenv("KRISTINE_HOME")
        ?: System.getenv("APPDATA")?.let { "$it/Kristine" }
        ?: ((System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share") + "/Kristine")
    SystemFileSystem.createDirectories(Path(home))
    return home
}
