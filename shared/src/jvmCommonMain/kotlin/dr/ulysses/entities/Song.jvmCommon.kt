package dr.ulysses.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

// Desktop stores file:/// URIs, Android plain paths
internal fun localFilePath(path: String) = path.removePrefix("file:///")

actual fun fileExists(path: String): Boolean = SystemFileSystem.exists(Path(localFilePath(path)))

/**
 * Writes [bytes] to the new file [file] with its folders; false when a file is there already or writing fails.
 */
internal suspend fun writeNewFile(
    file: String,
    bytes: ByteArray,
): Boolean = withContext(Dispatchers.IO) {
    val target = Path(file)
    if (SystemFileSystem.exists(target)) {
        return@withContext false
    }
    runCatching {
        target.parent?.let(SystemFileSystem::createDirectories)
        // Written aside first, so a canceled download never leaves a cut-off song behind. It keeps the song's
        // extension: Android lets apps create only audio files in the music folder
        val partial = Path("${file.substringBeforeLast('.')}.part.${file.substringAfterLast('.')}")
        SystemFileSystem.sink(partial).buffered().use { it.write(bytes) }
        SystemFileSystem.atomicMove(
            source = partial,
            destination = target
        )
    }.isSuccess
}
