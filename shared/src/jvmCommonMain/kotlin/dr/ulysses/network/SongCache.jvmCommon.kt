package dr.ulysses.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

internal actual class SongCache {
    private val directory = Path(
        SystemTemporaryDirectory,
        "kristine-songs"
    )
    private val mutex = Mutex()

    actual suspend fun pathFor(
        serverPath: String,
        download: suspend () -> ByteArray,
    ): String? = mutex.withLock {
        val file = fileFor(serverPath)
        if (!SystemFileSystem.exists(file)) {
            val bytes = download()
            withContext(Dispatchers.IO) {
                SystemFileSystem.createDirectories(directory)
                // Written aside first, so a canceled download never leaves a cut-off song behind
                val partial = Path(
                    directory,
                    "${file.name}.part"
                )
                SystemFileSystem.sink(partial).buffered().use { it.write(bytes) }
                SystemFileSystem.atomicMove(
                    source = partial,
                    destination = file
                )
            }
        }
        file.toString()
    }

    actual suspend fun retain(serverPaths: Set<String>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!SystemFileSystem.exists(directory)) {
                return@withContext
            }
            val keep = serverPaths.mapTo(HashSet()) { fileFor(it).name }
            SystemFileSystem.list(directory).filter { it.name !in keep }.forEach { SystemFileSystem.delete(it) }
        }
    }

    // The extension stays, so the player recognizes the format
    private fun fileFor(serverPath: String) = Path(
        directory,
        "${serverPath.hashCode().toUInt()}.${serverPath.substringAfterLast('.')}"
    )
}
