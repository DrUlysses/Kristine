package dr.ulysses

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

interface Directories {
    val filesystem: FileSystem
    val userConfig: Path
    val userData: Path
    val userCache: Path
}

class FakeStarAppDirs(override val filesystem: FileSystem) : Directories {
    override val userConfig: Path = "/userConfig".toPath().also(filesystem::createDirectories)
    override val userData: Path = "/userData".toPath().also(filesystem::createDirectories)
    override val userCache: Path = "/userCache".toPath().also(filesystem::createDirectories)
}
