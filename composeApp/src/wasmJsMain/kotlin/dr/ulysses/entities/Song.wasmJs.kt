package dr.ulysses.entities

actual suspend fun refreshSongs(): List<Song> {
//    val songsRootPath = Path(SettingsRepository.get("songsPath")?.value ?: ".")
//    val songs = SystemFileSystem.list(songsRootPath).filter {
//        it.name.substringBeforeLast(".") in listOf("mp3", "flac", "wav", "ogg", "m4a", "wma", "aac")
//    }.map {
//        Song(
//            path = "file:///$it",
//            title = it.name,
//            artist = "Unknown",
//        )
//    }
//
//    songs.forEach {
//        SongRepository.upsert(it)
//    }

    return emptyList() // Disable for now (coz file system is not available in wasm)
//    return songs
}
