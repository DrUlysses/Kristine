package dr.ulysses.entities

import java.io.File
import java.nio.file.Paths

actual suspend fun refreshSongs(): List<Song> {
    val songsRootPath = (SettingsRepository.get("songsPath")?.value ?: (Paths.get("")
        .toAbsolutePath().parent.toString() + "/music"))

    val songs = File(songsRootPath).walkTopDown().filter {
        it.extension in listOf("mp3", "flac", "wav", "ogg", "m4a", "wma", "aac")
    }.map {
        Song(
            path = "file:///" + it.absolutePath,
            title = it.nameWithoutExtension,
            artist = "Unknown",
        )
    }

    songs.forEach {
        SongRepository.upsert(it)
    }

    return songs.toList()
}
