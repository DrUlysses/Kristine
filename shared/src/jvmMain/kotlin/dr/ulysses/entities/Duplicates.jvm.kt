package dr.ulysses.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual suspend fun deleteSongFile(path: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        SystemFileSystem.delete(Path(localFilePath(path)))
    }.isSuccess
}
