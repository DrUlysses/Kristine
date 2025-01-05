package dr.ulysses.entities

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual suspend fun refreshSongs(): List<Song> {
    val songsRootPath = Path(SettingsRepository.get("songsPath")?.value ?: ".")
    val songs = SystemFileSystem.list(songsRootPath).filter {
        it.name.substringBeforeLast(".") in listOf("mp3", "flac", "wav", "ogg", "m4a", "wma", "aac")
    }.map {
        Song(
            path = "file:///$it",
            title = it.name,
            artist = "Unknown",
        )
    }.toList()

    songs.forEach {
        SongRepository.upsert(it)
    }

    return songs
}
