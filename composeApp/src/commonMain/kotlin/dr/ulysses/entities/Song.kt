package dr.ulysses.entities

import dr.ulysses.database.PlaylistSongQueries
import dr.ulysses.database.SharedDatabase
import dr.ulysses.database.SongQueries
import dr.ulysses.entities.base.Searchable
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
    val artwork: ByteArray? = null,
    val duration: Int? = null,
    val state: String,
    val playlists: List<Playlist> = emptyList(),
) : Searchable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Song

        if (path != other.path) return false
        if (title != other.title) return false
        if (album != other.album) return false
        if (artist != other.artist) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + artist.hashCode()
        return result
    }
}

object SongRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    suspend fun upsert(song: Song) = sharedDatabase { appDatabase ->
        appDatabase.songQueries.transactionWithResult {
            try {
                appDatabase.songQueries.insert(
                    path = song.path,
                    title = song.title,
                    album = song.album,
                    artist = song.artist,
                    artwork = song.artwork,
                    duration = song.duration?.toLong() ?: 0,
                    state = song.state,
                )
            } catch (_: Exception) {
                appDatabase.songQueries.update(
                    path = song.path,
                    title = song.title,
                    album = song.album,
                    artist = song.artist,
                    artwork = song.artwork,
                    duration = song.duration?.toLong() ?: 0,
                    state = song.state,
                )
            }
        }
    }

    suspend fun getAllSongs(): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAllSongs().executeAsList().map {
            Song(
                path = it.path,
                title = it.title,
                artist = it.artist,
                album = it.album,
                state = ""
            )
        }
    }

    suspend fun getAllArtists(): List<String> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAllArtists().executeAsList()
    }

    suspend fun getAllAlbums(): List<String> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAllAlbums().executeAsList().mapNotNull { it.album }
    }

    suspend fun getArtwork(path: String): ByteArray? = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectArtworkByPath(path).executeAsOneOrNull()?.artwork
    }

    suspend fun search(input: String): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.search(input).executeAsList().map {
            Song(
                path = it.path,
                title = it.title,
                artist = it.artist,
                album = it.album,
                state = ""
            )
        } + appDatabase.playlistSongQueries.search(input).executeAsList().flatMap { playlistSong ->
            appDatabase.songQueries.selectByPath(playlistSong.song_path).executeAsList().map {
                Song(
                    path = it.path,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    state = ""
                )
            }
        }
    }.distinct()
}

expect suspend fun refreshSongs(): List<Song>

// There should be some sql query to replace this hack, but I'm too lazy to write it
fun SongQueries.search(input: String) = search(input, input, input)
fun PlaylistSongQueries.search(input: String) = search(input, input)
