package dr.ulysses.entities

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import dr.ulysses.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.io.ByteArrayOutputStream
import java.io.File

/** Artwork isn't stored during the scan on Android, so lists load it from the file when shown. */
@RequiresApi(Build.VERSION_CODES.Q)
suspend fun loadArtworkFromFile(path: String): ByteArray? = withContext(Dispatchers.IO) {
    val context: Context by inject(Context::class.java)
    try {
        val file = File(path.substringAfter("file:///"))
        if (!file.exists()) {
            return@withContext null
        }

        ByteArrayOutputStream().use { stream ->
            ThumbnailUtils.createAudioThumbnail(
                /* file = */ file,
                /* size = */ Size(
                    context.resources.getDimensionPixelSize(android.R.dimen.thumbnail_width),
                    context.resources.getDimensionPixelSize(android.R.dimen.thumbnail_height)
                ),
                /* signal = */ null
            ).compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Logger.e(e) { "Couldn't load the artwork of $path" }
        null
    }
}
