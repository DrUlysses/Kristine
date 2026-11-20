package dr.ulysses.entities

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.java.KoinJavaComponent.inject

@Suppress("DEPRECATION") // the public Music folder by path: the app reads and writes songs by path everywhere
actual suspend fun musicFolder(): String? =
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath

// shortcut: a plain rename, which Android 11+ refuses for files another app created; those need
// MediaStore.createWriteRequest and the user's consent
actual suspend fun moveSongFile(
    from: String,
    to: String,
): Boolean = withContext(Dispatchers.IO) {
    val source = localFilePath(from)
    val target = localFilePath(to)
    val moved = runCatching {
        Path(target).parent?.let(SystemFileSystem::createDirectories)
        SystemFileSystem.atomicMove(
            source = Path(source),
            destination = Path(target)
        )
    }.isSuccess
    if (moved) {
        // MediaStore learns that the old file is gone and the new one is there; otherwise a scan brings back the old
        val context: Context by inject(Context::class.java)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(
                source,
                target
            ),
            null,
            null
        )
    }
    moved
}
