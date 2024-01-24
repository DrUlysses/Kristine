package dr.ulysses.entities

import dr.ulysses.database.SharedDatabase
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class Song(
    val id: Int? = null,
    val title: String,
    val album: String? = null,
    val artist: String,
    val path: String? = null,
    val duration: Int? = null,
    val status: String
)

class SongRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    private fun mapSong(
        id: Long,
        title: String,
        album: String?,
        artist: String,
        path: String?,
        duration: Long?,
        status: String?,
    ): Song = Song(
        id = id.toInt(),
        title = title,
        album = album,
        artist = artist,
        path = path,
        duration = duration?.toInt() ?: 0,
        status = status ?: ""
    )

    suspend fun insert(song: Song) = sharedDatabase { appDatabase ->
        appDatabase.songQueries.transactionWithResult {
            appDatabase.songQueries.insert(
                title = song.title,
                album = song.album,
                artist = song.artist,
                path = song.path,
                duration = song.duration?.toLong() ?: 0,
                status = song.status
            )
        }
    }

    suspend fun getAll(): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAll(::mapSong).executeAsList()
    }
}

expect suspend fun refreshSongs(): List<Song>
