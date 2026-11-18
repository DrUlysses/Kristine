package dr.ulysses.entities

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Android-specific function to load artwork from a file.
 * This function is used to load artwork on-demand when needed.
 */
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
        e.printStackTrace()
        null
    }
}
