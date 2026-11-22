package dr.ulysses.entities

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import dr.ulysses.api.internal.*
import dr.ulysses.database.Database
import dr.ulysses.database.SharedDatabase
import dr.ulysses.database.SongQueries
import dr.ulysses.entities.base.Searchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import dr.ulysses.database.Song as SongRow

/**
 * Song entity, used for internal representation of songs.
 *
 * @param path URL if remote, path if local. Primary key
 * @param duration Duration of the song in seconds
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
    /**
     * The file's name without its folder and extension.
     */
    @Transient
    val fileName = path.substringAfterLast('/').substringBeforeLast('.')

    enum class State {
        Downloaded,
        Sorted,
        Unsorted,
        NotDownloaded;

        companion object {
            fun fromString(value: String?) = entries.find { it.name == value } ?: Downloaded
        }
    }

    // Identity is the metadata, not the artwork bytes or the state
    override fun equals(other: Any?): Boolean =
        other is Song &&
                path == other.path &&
                title == other.title &&
                album == other.album &&
                artist == other.artist

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + artist.hashCode()
        return result
    }
}

object SongRepository : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    /**
     * Brings the library in line with a scan without creating duplicates. A known song always keeps its
     * edits, artwork, state and playlists:
     * - its file is gone: it takes the path of the scanned file with the same name and the most matching
     *   parent folders. If that file already has an entry, the entry only makes way when the scan itself
     *   created it (Downloaded, in no playlist); otherwise both stay for the user to resolve.
     * - no unique match: it becomes [Song.State.NotDownloaded]
     * - its file is back: NotDownloaded becomes Downloaded
     * New files are added.
     */
    suspend fun syncWithScan(scanned: List<Song>) = sharedDatabase { db ->
        val songs = db.songQueries
        songs.transaction {
            val stored = songs.selectPathStates().awaitAsList().associate { it.path to Song.State.fromString(it.state) }
            val scannedPaths = scanned.mapTo(HashSet()) { it.path }
            val scannedByName = scanned.groupBy(
                keySelector = { fileName(it.path) },
                valueTransform = { it.path }
            )
            val claimed = HashSet<String>()

            suspend fun makesWay(path: String) = stored[path] == Song.State.Downloaded &&
                    db.playlistSongQueries.countBySong(path).awaitAsOne() == 0L

            for ((path, state) in stored) {
                if (path in scannedPaths || fileExists(path)) {
                    if (state == Song.State.NotDownloaded) {
                        songs.updateState(
                            state = Song.State.Downloaded.name,
                            path = path
                        )
                    }
                    continue
                }
                val target = closestPath(
                    path = path,
                    candidates = scannedByName[fileName(path)].orEmpty()
                )
                    ?.takeIf { it !in claimed && (it !in stored || makesWay(it)) }
                if (target == null) {
                    if (state != Song.State.NotDownloaded) {
                        songs.updateState(
                            state = Song.State.NotDownloaded.name,
                            path = path
                        )
                    }
                    continue
                }
                claimed += target
                if (target in stored) {
                    songs.deleteByPath(target)
                }
                songs.updatePath(
                    newPath = target,
                    oldPath = path
                )
                if (state == Song.State.NotDownloaded) {
                    songs.updateState(
                        state = Song.State.Downloaded.name,
                        path = target
                    )
                }
            }
            // Known and remapped paths are skipped by the OR IGNORE
            scanned.forEach { songs.insertOrIgnore(it) }
        }
    }

    /** Points the entry at [oldPath] to the new file at [newPath], e.g. after downloading it. */
    suspend fun relink(
        oldPath: String,
        newPath: String,
    ) = sharedDatabase { db ->
        val songs = db.songQueries
        songs.transaction {
            // A scan during the download may already have added the new file as its own entry
            if (newPath != oldPath) {
                songs.deleteByPath(newPath)
            }
            songs.updatePath(
                newPath = newPath,
                oldPath = oldPath
            )
            songs.updateState(
                state = Song.State.Downloaded.name,
                path = newPath
            )
        }
    }

    /** The entry follows its file to [newPath]; playlists follow through ON UPDATE CASCADE. */
    suspend fun move(
        oldPath: String,
        newPath: String,
    ) = sharedDatabase { db ->
        db.songQueries.updatePath(
            newPath = newPath,
            oldPath = oldPath
        )
    }

    /** Removes the entry at [oldPath], a copy of the song at [keepPath], which takes its place in playlists. */
    suspend fun mergeInto(
        oldPath: String,
        keepPath: String,
    ) = sharedDatabase { db ->
        db.songQueries.transaction {
            db.playlistSongQueries.moveSong(
                newPath = keepPath,
                oldPath = oldPath
            )
            db.songQueries.deleteByPath(oldPath)
        }
    }

    private fun fileName(path: String) = path.substringAfterLast('/')

    /**
     * This library's copy of a song from another device: the same path, else the same file name in the most
     * similar folders (as in [syncWithScan]), else the only song with the same title and artist.
     */
    suspend fun findMatch(song: Song): Song? = findMatches(listOf(song)).single()

    /** [findMatch] for many songs at once, e.g. a whole queue, reading the library only once. */
    suspend fun findMatches(songs: List<Song>): List<Song?> {
        val library = getAllSongs().filter { it.state != Song.State.NotDownloaded }
        val byPath = library.associateBy { it.path }
        val byFileName = library.groupBy { fileName(it.path) }
        val byTitleAndArtist = library.groupBy { it.title.lowercase() to it.artist.lowercase() }
        return songs.map { song ->
            byPath[song.path] ?: byFileName[fileName(song.path)]?.let { sameName ->
                closestPath(
                    path = song.path,
                    candidates = sameName.map { it.path }
                )?.let { path -> sameName.first { it.path == path } }
            } ?: byTitleAndArtist[song.title.lowercase() to song.artist.lowercase()]?.singleOrNull()
        }
    }

    /** The candidate sharing the most trailing path segments with [path], or null on a tie. */
    private fun closestPath(
        path: String,
        candidates: List<String>,
    ): String? {
        val segments = path.split('/').asReversed()
        val scored = candidates.map { candidate ->
            candidate to candidate.split('/').asReversed().zip(segments).takeWhile { (a, b) -> a == b }.size
        }
        val best = scored.maxOfOrNull { it.second } ?: return null
        return scored.singleOrNull { it.second == best }?.first
    }

    suspend fun upsert(song: Song) = sharedDatabase { db ->
        db.songQueries.transaction {
            db.songQueries.insertOrIgnore(song)
            db.songQueries.update(song)
        }
    }

    @Serializable
    data class Progress(
        val processed: Int = 0,
        val total: Int = 0,
        val changed: Int = 0,
        val isComplete: Boolean = false,
    )

    fun removeNonExistingSongs(): Flow<Progress> {
        if (isRemote) {
            return LibraryTasks.maintenance(RemoveMissingSongsCommand)
        }
        return forEachSong { db, song ->
            val missing = isLocalFile(song.path) && !fileExists(song.path)
            if (missing) {
                db.songQueries.deleteByPath(song.path)
            }
            missing
        }
    }

    fun updateSongDurations(): Flow<Progress> {
        if (isRemote) {
            return LibraryTasks.maintenance(UpdateDurationsCommand)
        }
        return forEachSong { db, song ->
            if ((song.duration ?: 0L) != 0L || !isLocalFile(song.path) || !fileExists(song.path)) {
                return@forEachSong false
            }
            val duration = getDurationFromMetadata(song.path) ?: return@forEachSong false
            db.songQueries.update(
                song.toSong().copy(
                    duration = duration
                )
            )
            true
        }
    }

    private fun forEachSong(action: suspend (Database, SongRow) -> Boolean): Flow<Progress> = flow {
        sharedDatabase { db ->
            val songs = db.songQueries.selectAll().awaitAsList()
            var changed = 0
            emit(
                Progress(
                    total = songs.size
                )
            )
            songs.forEachIndexed { index, song ->
                if (action(db, song)) {
                    changed++
                }
                emit(
                    Progress(
                        processed = index + 1,
                        total = songs.size,
                        changed = changed
                    )
                )
            }
            emit(
                Progress(
                    processed = songs.size,
                    total = songs.size,
                    changed = changed,
                    isComplete = true
                )
            )
        }
    }.flowOn(Dispatchers.Default)

    suspend fun getAllSongs(): List<Song> {
        if (isRemote) {
            return LibraryClient.allSongs()
        }
        return sharedDatabase { db ->
            db.songQueries.selectAllSongs().awaitAsList().map {
                Song(
                    path = it.path,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    duration = it.duration?.toInt(),
                    state = Song.State.fromString(it.state),
                )
            }
        }
    }

    suspend fun getAllArtists(): List<String> = sharedDatabase { it.songQueries.selectAllArtists().awaitAsList() }

    suspend fun getAllAlbums(): List<String> = sharedDatabase { db ->
        db.songQueries.selectAllAlbums().awaitAsList().mapNotNull { it.album }
    }

    suspend fun getArtwork(path: String): ByteArray? = sharedDatabase { db ->
        db.songQueries.selectArtworkByPath(path).awaitAsOneOrNull()?.artwork
    }

    suspend fun search(query: String): List<Song> {
        if (isRemote) {
            return LibraryClient.searchSongs(query)
        }
        return sharedDatabase { db ->
            val bySong = db.songQueries.search(query).awaitAsList().map {
                Song(
                    path = it.path,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    duration = it.duration?.toInt(),
                    state = Song.State.fromString(it.state),
                )
            }
            val byPlaylist = db.playlistSongQueries.search(query).awaitAsList().flatMap {
                db.songQueries.selectByPath(it.song_path).awaitAsList().map { row ->
                    row.toSong().copy(
                        artwork = null
                    )
                }
            }
            (bySong + byPlaylist).distinct()
        }
    }

    suspend fun getByNotState(notState: Song.State): List<Song> {
        if (isRemote) {
            return LibraryClient.songsByNotState(notState)
        }
        return sharedDatabase { db ->
            db.songQueries.selectByNotState(notState.name).awaitAsList().map { it.toSong() }
        }
    }

    suspend fun getByPathOrNull(path: String): Song? {
        if (isRemote) {
            return LibraryClient.songByPath(path)
        }
        return sharedDatabase { db ->
            db.songQueries.getOrNull(path).awaitAsOneOrNull()?.toSong()
        }
    }

    private fun SongRow.toSong() = Song(
        path = path,
        title = title,
        artist = artist,
        album = album,
        duration = duration?.toInt(),
        artwork = artwork,
        state = Song.State.fromString(state),
    )

    private suspend fun SongQueries.insertOrIgnore(song: Song) = insertOrIgnore(
        path = song.path,
        title = song.title,
        album = song.album,
        artist = song.artist,
        artwork = song.artwork,
        duration = song.duration?.toLong() ?: 0,
        state = song.state.name,
    )

    private suspend fun SongQueries.update(song: Song) = update(
        path = song.path,
        title = song.title,
        album = song.album,
        artist = song.artist,
        artwork = song.artwork,
        duration = song.duration?.toLong() ?: 0,
        state = song.state.name,
    )

    // Android stores plain paths, desktop stores file:/// URIs; anything else is remote
    private fun isLocalFile(path: String) = path.startsWith("file:") || path.startsWith("/")
}

expect suspend fun refreshSongs(): List<Song>
expect fun fileExists(path: String): Boolean

/** Duration in seconds read from the file's metadata, or null if it can't be read. */
expect fun getDurationFromMetadata(path: String): Int?
