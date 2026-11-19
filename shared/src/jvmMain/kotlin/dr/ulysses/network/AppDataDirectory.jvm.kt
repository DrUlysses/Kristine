package dr.ulysses.network

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

// Next to the database, like DriverFactory's folder
internal actual fun appDataDirectory(): String = SystemFileSystem.resolve(Path(".")).parent.toString()
