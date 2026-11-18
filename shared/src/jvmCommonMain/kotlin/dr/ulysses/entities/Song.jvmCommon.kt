package dr.ulysses.entities

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

// Desktop stores file:/// URIs, Android plain paths
actual fun fileExists(path: String): Boolean = SystemFileSystem.exists(Path(path.removePrefix("file:///")))
