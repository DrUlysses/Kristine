package dr.ulysses.entities

import dr.ulysses.SUPPORTED_EXTENSIONS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

actual suspend fun refreshSongs(): List<Song> = withContext(Dispatchers.IO) {
    // jaudiotagger only reads java.io.File, so the scan walks Files too
    val songsRoot = File(songsFolder())
    // An unmounted drive would otherwise mark the whole library NotDownloaded
    if (!songsRoot.isDirectory) {
        return@withContext SongRepository.getAllSongs()
    }
    val songs = songsRoot.walkTopDown()
        .filter { it.extension in SUPPORTED_EXTENSIONS }
        .mapNotNull { file ->
            // Unreadable or tagless files are skipped, not fatal for the whole scan
            runCatching {
                val audio = AudioFileIO.read(file)
                Song(
                    path = "file:///" + file.absolutePath,
                    title = audio.tag?.getFirst(FieldKey.TITLE).orEmpty().ifBlank { file.nameWithoutExtension },
                    artist = audio.tag?.getFirst(FieldKey.ARTIST).orEmpty().ifBlank { "Unknown artist" },
                    album = audio.tag?.getFirst(FieldKey.ALBUM).orEmpty().ifBlank { "Unknown album" },
                    artwork = audio.tag?.firstArtwork?.binaryData,
                    duration = audio.audioHeader.trackLength,
                )
            }.getOrNull()
        }
        .toList()

    SongRepository.syncWithScan(songs)
    songs
}

actual fun getDurationFromMetadata(path: String): Int? =
    runCatching { AudioFileIO.read(File(path.substringAfter("file:///"))).audioHeader.trackLength }.getOrNull()
