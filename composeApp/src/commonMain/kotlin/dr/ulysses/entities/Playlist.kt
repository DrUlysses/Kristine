package dr.ulysses.entities

import dr.ulysses.database.SharedDatabase
import dr.ulysses.entities.base.Searchable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class Playlist(
    val name: String = "Unnamed",
    val songs: List<Song> = emptyList(),
    val artwork: ByteArray? = null,
    val duration: Int? = null,
    val state: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
) : Searchable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Playlist

        if (name != other.name) return false
        if (songs != other.songs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + songs.hashCode()
        return result
    }
}

object PlaylistRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    suspend fun rename(oldName: String, newName: String) = sharedDatabase { appDatabase ->
        appDatabase.playlistQueries.transactionWithResult {
            appDatabase.playlistQueries.rename(
                name = oldName,
                updated_at = Clock.System.now().toString(),
                name_ = newName
            )
        }
    }

    suspend fun insert(playlist: Playlist) = sharedDatabase { appDatabase ->
        appDatabase.playlistQueries.transactionWithResult {
            appDatabase.playlistQueries.insert(
                name = playlist.name,
                created_at = playlist.createdAt.toString(),
                updated_at = playlist.updatedAt.toString(),
            )
            playlist.songs.forEach { song ->
                appDatabase.playlistSongQueries.insert(
                    playlist_name = playlist.name,
                    song_path = song.path,
                )
            }
        }
    }

    suspend fun getAllPlaylists(): List<Playlist> = sharedDatabase { appDatabase ->
        appDatabase.playlistQueries.selectAll().executeAsList().map {
            Playlist(
                name = it.name,
                songs = emptyList(),
                artwork = it.artwork,
                state = it.state ?: "",
                createdAt = Instant.parse(it.created_at),
                updatedAt = Instant.parse(it.updated_at),
            )
        }
    }

    suspend fun getPlaylistSongs(playlistName: String): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.playlistSongQueries.selectSongsByPlaylist(playlistName).executeAsList().map {
            Song(
                path = it.path,
                title = it.title,
                album = it.album,
                artist = it.artist,
                state = it.state ?: "",
            )
        }
    }

    suspend fun getArtwork(playlistName: String) = sharedDatabase { appDatabase ->
        appDatabase.playlistQueries.selectArtworkByName(playlistName).executeAsOneOrNull()?.artwork
    }

    suspend fun search(input: String): List<Playlist> = sharedDatabase { appDatabase ->
        appDatabase.playlistQueries.search(input).executeAsList().map {
            Playlist(
                name = it.name,
                songs = emptyList(),
                artwork = it.artwork,
                state = it.state ?: "",
                createdAt = Instant.parse(it.created_at),
                updatedAt = Instant.parse(it.updated_at),
            )
        } + appDatabase.playlistSongQueries.search(input).executeAsList().flatMap { playlistSong ->
            appDatabase.playlistQueries.selectByName(playlistSong.playlist_name).executeAsList().map {
                Playlist(
                    name = it.name,
                    songs = emptyList(),
                    artwork = it.artwork,
                    state = it.state ?: "",
                    createdAt = Instant.parse(it.created_at),
                    updatedAt = Instant.parse(it.updated_at),
                )
            }
        }
    }.distinct()
}
