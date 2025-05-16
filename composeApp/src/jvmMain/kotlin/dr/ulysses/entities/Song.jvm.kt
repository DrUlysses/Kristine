package dr.ulysses.entities

import dr.ulysses.SUPPORTED_EXTENSIONS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

actual suspend fun refreshSongs(): List<Song> {
    val songsRootPath: String =
        (SettingsRepository.get(SettingKey.SongsPath)?.value ?: Path("./music").absolutePathString())

    val songs = Path(songsRootPath).toFile().walkTopDown().filter {
        it.extension in SUPPORTED_EXTENSIONS
    }.mapNotNull { song ->
        runCatching {
            AudioFileIO.read(song).let { tags ->
                Song(
                    path = "file:///" + song.absolutePath,
                    title = runCatching { tags.tag.getFirst(FieldKey.TRACK) }.getOrDefault("Unnamed"),
                    artist = runCatching { tags.tag.getFirst(FieldKey.ARTIST) }.getOrDefault("Unknown artist"),
                    album = runCatching { tags.tag.getFirst(FieldKey.ALBUM) }.getOrDefault("Unknown album"),
                    artwork = runCatching { tags.tag.firstArtwork.binaryData }.getOrNull(),
                    duration = runCatching { tags.audioHeader.trackLength }.getOrNull()
                )
            }
        }.getOrNull()
    }

    songs.forEach {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            SongRepository.upsert(it)
        }
    }

    return songs.toList()
}
