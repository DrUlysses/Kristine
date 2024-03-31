package dr.ulysses.entities

import dr.ulysses.database.SharedDatabase
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Song entity, used for internal representation of songs.
 *
 * @param path URL if remote, path if local
 * @param title Title of the song
 * @param album Album of the song
 * @param artist Artist of the song
 * @param duration Duration of the song in seconds
 * @param state State of the song, can be "downloaded"
 */
@Serializable
data class Song(
    val path: String,
    val title: String,
    val album: String? = null,
    val artist: String,
    val duration: Int? = null,
    val state: String
)

object SongRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    private fun mapSong(
        path: String,
        title: String,
        album: String?,
        artist: String,
        duration: Long?,
        state: String?,
    ): Song = Song(
        path = path,
        title = title,
        album = album,
        artist = artist,
        duration = duration?.toInt() ?: 0,
        state = state ?: ""
    )

    suspend fun upsert(song: Song) = sharedDatabase { appDatabase ->
        appDatabase.songQueries.transactionWithResult {
            try {
                appDatabase.songQueries.insert(
                    path = song.path,
                    title = song.title,
                    album = song.album,
                    artist = song.artist,
                    duration = song.duration?.toLong() ?: 0,
                    state = song.state,
                )
            } catch (e: Exception) {
                appDatabase.songQueries.update(
                    path = song.path,
                    title = song.title,
                    album = song.album,
                    artist = song.artist,
                    duration = song.duration?.toLong() ?: 0,
                    state = song.state,
                )
            }
        }
    }

    suspend fun getAll(): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAll(::mapSong).executeAsList()
    }

    suspend fun getAllArtists(): List<String> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAllArtists().executeAsList()
    }
}

expect suspend fun refreshSongs(): List<Song>
