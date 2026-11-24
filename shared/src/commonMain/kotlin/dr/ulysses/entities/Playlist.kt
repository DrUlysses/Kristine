@file:OptIn(ExperimentalTime::class)

package dr.ulysses.entities

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dr.ulysses.api.internal.LibraryClient
import dr.ulysses.api.internal.PlaylistSaveRequest
import dr.ulysses.api.internal.isRemote
import dr.ulysses.database.SharedDatabase
import dr.ulysses.entities.base.Searchable
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class Playlist(
    val name: String = "Unnamed",
    val songs: List<Song> = emptyList(),
    val artwork: ByteArray? = null,
    val duration: Int? = null,
    val state: State = State(),
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
) : Searchable {
    @Serializable
    data class State(
        val state: StateName = StateName.Stopped,
        val position: Int = 0,
    ) {
        enum class StateName {
            Playing,
            PlayingShuffled,
            Paused,
            PausedShuffled,
            Stopped,
            StoppedShuffled
        }

        override fun toString() = "$state $position"

        companion object {
            fun fromString(value: String?) = State(
                state = StateName.valueOf(value?.split(" ")?.firstOrNull() ?: StateName.Stopped.name),
                position = value?.split(" ")?.lastOrNull()?.toInt() ?: 0
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || this::class != other::class) {
            return false
        }

        other as Playlist

        if (name != other.name) {
            return false
        }
        if (songs != other.songs) {
            return false
        }

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

    /**
     * Stores [playlist] with its songs in their order, in place of the playlist named [oldName], or as a new one when
     * that's null. Fails, changing nothing, when another playlist has the name.
     */
    suspend fun save(
        playlist: Playlist,
        oldName: String?,
    ) {
        if (isRemote) {
            LibraryClient.savePlaylist(
                PlaylistSaveRequest(
                    playlist = playlist,
                    oldName = oldName
                )
            )
            return
        }
        sharedDatabase { appDatabase ->
            appDatabase.playlistQueries.transaction {
                if (oldName != null) {
                    appDatabase.playlistSongQueries.deleteByPlaylist(oldName)
                    appDatabase.playlistQueries.deleteByName(oldName)
                }
                appDatabase.playlistQueries.insert(
                    name = playlist.name,
                    artwork = playlist.artwork,
                    state = playlist.state.toString(),
                    created_at = playlist.createdAt.toString(),
                    updated_at = Clock.System.now().toString(),
                )
                playlist.songs.forEachIndexed { index, song ->
                    appDatabase.playlistSongQueries.insert(
                        playlist_name = playlist.name,
                        song_path = song.path,
                        position = index.toLong(),
                    )
                }
            }
        }
    }

    suspend fun delete(name: String) {
        if (isRemote) {
            LibraryClient.deletePlaylist(name)
            return
        }
        sharedDatabase { appDatabase ->
            appDatabase.playlistQueries.transaction {
                appDatabase.playlistSongQueries.deleteByPlaylist(name)
                appDatabase.playlistQueries.deleteByName(name)
            }
        }
    }

    suspend fun getAllPlaylists(): List<Playlist> {
        if (isRemote) {
            return LibraryClient.playlists()
        }
        return sharedDatabase { appDatabase ->
            appDatabase.playlistQueries.selectAll().awaitAsList().map {
                Playlist(
                    name = it.name,
                    songs = emptyList(),
                    artwork = it.artwork,
                    state = Playlist.State.fromString(it.state),
                    createdAt = Instant.parse(it.created_at),
                    updatedAt = Instant.parse(it.updated_at),
                )
            }
        }
    }

    suspend fun getPlaylistSongs(playlistName: String): List<Song> {
        if (isRemote) {
            return LibraryClient.playlistSongs(playlistName)
        }
        return sharedDatabase { appDatabase ->
            appDatabase.playlistSongQueries.selectSongsByPlaylist(playlistName).awaitAsList().map {
                Song(
                    path = it.path,
                    title = it.title,
                    album = it.album,
                    artist = it.artist,
                    state = Song.State.fromString(it.state),
                )
            }
        }
    }

    suspend fun getArtwork(playlistName: String): ByteArray? {
        if (isRemote) {
            return LibraryClient.playlistArtwork(playlistName)
        }
        return sharedDatabase { appDatabase ->
            appDatabase.playlistQueries.selectArtworkByName(playlistName).awaitAsOneOrNull()?.artwork
        }
    }

    suspend fun search(input: String): List<Playlist> {
        if (isRemote) {
            return LibraryClient.searchPlaylists(input)
        }
        return sharedDatabase { appDatabase ->
            appDatabase.playlistQueries.search(input).awaitAsList().map {
                Playlist(
                    name = it.name,
                    songs = emptyList(),
                    artwork = it.artwork,
                    state = Playlist.State.fromString(it.state),
                    createdAt = Instant.parse(it.created_at),
                    updatedAt = Instant.parse(it.updated_at),
                )
            } + appDatabase.playlistSongQueries.search(input).awaitAsList().flatMap { playlistSong ->
                appDatabase.playlistQueries.selectByName(playlistSong.playlist_name).awaitAsList().map {
                    Playlist(
                        name = it.name,
                        songs = emptyList(),
                        artwork = it.artwork,
                        state = Playlist.State.fromString(it.state),
                        createdAt = Instant.parse(it.created_at),
                        updatedAt = Instant.parse(it.updated_at),
                    )
                }
            }
        }.distinct()
    }
}
