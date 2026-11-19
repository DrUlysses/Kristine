package dr.ulysses.entities

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.java.KoinJavaComponent.inject

// shortcut: a plain file delete, which Android 11+ refuses for files another app created; those need
// MediaStore.createDeleteRequest and the user's consent
actual suspend fun deleteSongFile(path: String): Boolean = withContext(Dispatchers.IO) {
    val file = localFilePath(path)
    val deleted = runCatching { SystemFileSystem.delete(Path(file)) }.isSuccess
    if (deleted) {
        // Otherwise MediaStore keeps listing the file, and the next scan brings the entry back
        val context: Context by inject(Context::class.java)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file),
            null,
            null
        )
    }
    deleted
}
