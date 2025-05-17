package dr.ulysses.entities

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dr.ulysses.database.PlaylistSongQueries
import dr.ulysses.database.SharedDatabase
import dr.ulysses.database.SongQueries
import dr.ulysses.entities.base.Searchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Song entity, used for internal representation of songs.
 *
 * @param path URL if remote, path if local. Primary key
 * @param title Title of the song
 * @param album Album of the song
 * @param artist Artist of the song
 * @param duration Duration of the song in seconds
 * @param state State of the song
 */
@Serializable
data class Song(
    val path: String,
    val title: String,
    val album: String? = null,
    val artist: String,
    val artwork: ByteArray? = null,
    val duration: Int? = null,
    val state: State = State.Downloaded,
    val playlists: List<Playlist> = emptyList(),
) : Searchable {
    enum class State {
        Downloaded,
        Sorted,
        Unsorted,
        NotDownloaded;

        override fun toString() = name

        companion object {
            fun fromString(value: String?) = value?.let { value ->
                entries.find { it.name == value }
            } ?: Downloaded
        }
    }

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
                    state = song.state.toString(),
                )
            } catch (_: Exception) {
                appDatabase.songQueries.update(
                    path = song.path,
                    title = song.title,
                    album = song.album,
                    artist = song.artist,
                    artwork = song.artwork,
                    duration = song.duration?.toLong() ?: 0,
                    state = song.state.toString(),
                )
            }
        }
    }

    /**
     * Represents the progress of the non-existing songs removal operation.
     * @param processed Number of songs processed so far
     * @param total Total number of songs to process
     * @param removed Number of songs removed so far
     * @param isComplete Whether the operation is complete
     */
    data class RemoveNonExistingProgress(
        val processed: Int = 0,
        val total: Int = 0,
        val removed: Int = 0,
        val isComplete: Boolean = false,
    )

    /**
     * Removes songs from the database where the path starts with "file" and the file doesn't exist on the device.
     * @return A Flow that emits progress updates during the operation
     */
    fun removeNonExistingSongs(): Flow<RemoveNonExistingProgress> = channelFlow {
        sharedDatabase { appDatabase ->
            val allSongs = appDatabase.songQueries.selectAll().awaitAsList()
            val totalSongs = allSongs.size

            // Send initial progress
            send(
                RemoveNonExistingProgress(
                    processed = 0,
                    total = totalSongs,
                    removed = 0,
                    isComplete = false
                )
            )

            var removedCount = 0
            var processedCount = 0
            val mutex = Mutex() // For thread safety of shared counters

            coroutineScope {
                val deferredResults = allSongs.map { song ->
                    async {
                        val path = song.path
                        val removed = if (path.startsWith("file") && !fileExists(path)) {
                            appDatabase.songQueries.deleteByPath(path)
                            1
                        } else {
                            0
                        }

                        // Update counters and send progress under mutex protection
                        mutex.withLock {
                            removedCount += removed
                            processedCount++

                            // Send progress update
                            send(
                                RemoveNonExistingProgress(
                                    processed = processedCount,
                                    total = totalSongs,
                                    removed = removedCount,
                                    isComplete = false
                                )
                            )
                        }
                    }
                }

                // Wait for all parallel operations to complete
                deferredResults.awaitAll()
            }

            // Send final progress with isComplete = true
            send(
                RemoveNonExistingProgress(
                    processed = totalSongs,
                    total = totalSongs,
                    removed = removedCount,
                    isComplete = true
                )
            )
        }
    }.flowOn(Dispatchers.Default) // Run on background thread

    suspend fun getAllSongs(): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAllSongs().awaitAsList().map {
            Song(
                path = it.path,
                title = it.title,
                artist = it.artist,
                album = it.album,
            )
        }
    }

    suspend fun getAllArtists(): List<String> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAllArtists().awaitAsList()
    }

    suspend fun getAllAlbums(): List<String> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectAllAlbums().awaitAsList().mapNotNull { it.album }
    }

    suspend fun getArtwork(path: String): ByteArray? = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectArtworkByPath(path).awaitAsOneOrNull()?.artwork
    }

    suspend fun search(input: String): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.search(input).awaitAsList().map {
            Song(
                path = it.path,
                title = it.title,
                artist = it.artist,
                album = it.album,
            )
        } + appDatabase.playlistSongQueries.search(input).awaitAsList().flatMap { playlistSong ->
            appDatabase.songQueries.selectByPath(playlistSong.song_path).awaitAsList().map {
                Song(
                    path = it.path,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                )
            }
        }
    }.distinct()

    suspend fun getByNotState(notState: Song.State): List<Song> = sharedDatabase { appDatabase ->
        appDatabase.songQueries.selectByNotState(notState.toString()).awaitAsList().map {
            Song(
                path = it.path,
                title = it.title,
                artist = it.artist,
                album = it.album,
                duration = it.duration?.toInt(),
                artwork = it.artwork,
                state = Song.State.fromString(it.state)
            )
        }
    }

    suspend fun getByPathOrNull(path: String): Song? = sharedDatabase { appDatabase ->
        appDatabase.songQueries.getOrNull(path).awaitAsOneOrNull()?.let {
            Song(
                path = it.path,
                title = it.title,
                artist = it.artist,
                album = it.album,
                duration = it.duration?.toInt(),
                artwork = it.artwork,
                state = Song.State.fromString(it.state)
            )
        }
    }
}

expect suspend fun refreshSongs(): List<Song>
expect fun fileExists(path: String): Boolean

// There should be some SQL query to replace this hack, but I'm too lazy to write it
fun SongQueries.search(input: String) = search(input, input, input)
fun PlaylistSongQueries.search(input: String) = search(input, input)
