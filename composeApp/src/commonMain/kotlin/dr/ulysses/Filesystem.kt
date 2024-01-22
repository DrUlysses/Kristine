package dr.ulysses

import okio.FileSystem
import okio.Path

interface Directories {
    val filesystem: FileSystem
    val userConfig: Path
    val userData: Path
    val userCache: Path
}
