package dr.ulysses.entities

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

// Desktop stores file:/// URIs, Android plain paths
internal fun localFilePath(path: String) = path.removePrefix("file:///")

actual fun fileExists(path: String): Boolean = SystemFileSystem.exists(Path(localFilePath(path)))
