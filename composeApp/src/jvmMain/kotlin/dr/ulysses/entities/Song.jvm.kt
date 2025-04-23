package dr.ulysses.entities

import dr.ulysses.SUPPORTED_EXTENSIONS
import java.io.File
import java.nio.file.Paths

actual suspend fun refreshSongs(): List<Song> {
    val songsRootPath = (SettingsRepository.get(SettingKey.SongsPath)?.value ?: (Paths.get("")
        .toAbsolutePath().parent.toString() + "/music"))

    val songs = File(songsRootPath).walkTopDown().filter {
        it.extension in SUPPORTED_EXTENSIONS
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
