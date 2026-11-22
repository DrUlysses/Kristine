package dr.ulysses.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

actual suspend fun localLyrics(path: String): String? = withContext(Dispatchers.IO) {
    val file = File(localFilePath(path))
    if (!file.exists()) {
        return@withContext null
    }
    lrcFile(file).takeIf { it.exists() }?.readText()
        ?: runCatching { AudioFileIO.read(file).tag?.getFirst(FieldKey.LYRICS) }.getOrNull()?.takeIf { it.isNotBlank() }
}

actual suspend fun saveLyricsFile(
    path: String,
    lyrics: String,
) {
    withContext(Dispatchers.IO) {
        // Android may refuse files that aren't audio in the music folder; the lyrics are then looked up again
        runCatching {
            val file = File(localFilePath(path))
            if (file.exists()) {
                lrcFile(file).writeText(lyrics)
            }
        }
    }
}

/** "Song.mp3" → "Song.lrc" in the same folder, as most players look for it. */
private fun lrcFile(song: File) = File(
    song.parentFile,
    "${song.nameWithoutExtension}.lrc"
)
