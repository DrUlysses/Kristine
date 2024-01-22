package dr.ulysses.repositories

import dr.ulysses.entities.Song

interface SongRepository {
    fun add(song: Song)
    fun getAll(): List<Song>
}

class SongRepositoryImpl : SongRepository {
    private val _songs = arrayListOf<Song>()

    override fun add(song: Song) {
        _songs.add(song)
    }

    override fun getAll(): List<Song> {
        return _songs
    }
}
