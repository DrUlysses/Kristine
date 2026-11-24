package dr.ulysses.entities

import dr.ulysses.Logger
import dr.ulysses.api.internal.LibraryClient
import dr.ulysses.models.MainViewModel
import dr.ulysses.network.NetworkManager
import dr.ulysses.ui.elements.songs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One way from the connected Kristine server to this device: this device's unsorted songs take the tags of the
 * server's sorted copies, found by name or sound, and the server's songs missing here can be downloaded.
 */
object Sync {
    data class Match(
        val own: Song,
        val server: Song,
    )

    data class State(
        /**
         * What runs now, with its progress; null when nothing does.
         */
        val step: String? = null,
        val done: Int = 0,
        val total: Int = 0,
        val toSort: List<Match> = emptyList(),
        val missing: List<Song> = emptyList(),
        val isCompared: Boolean = false,
        /**
         * What the last sync did.
         */
        val result: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState
    private val writing = Mutex()
    private const val PARALLEL_DOWNLOADS = 3

    /**
     * Finds what to sort here and what is missing here; the fingerprints of songs whose names match nothing are
     * computed on this device and compared on the server.
     */
    fun compare() {
        if (mutableState.value.step != null) {
            return
        }
        mutableState.value = State(
            step = "Reading both libraries"
        )
        scope.launch {
            runCatching {
                val own = SongRepository.ownSongs().filter { it.state != Song.State.NotDownloaded }
                val server = LibraryClient.allSongs().filter { it.state != Song.State.NotDownloaded }
                val unsorted = own.filter { it.state != Song.State.Sorted }
                val byName = unsorted.zip(
                    SongRepository.findMatches(
                        songs = unsorted,
                        library = server.filter { it.state == Song.State.Sorted }
                    )
                )
                val bySound = bySound(byName.filter { it.second == null }.map { it.first })
                val toSort = byName.mapNotNull { (song, match) ->
                    (match ?: bySound[song.path])?.let {
                        Match(
                            own = song,
                            server = it
                        )
                    }
                }
                val sortedHere = toSort.mapTo(HashSet()) { it.server.path }
                val missing = server.zip(
                    SongRepository.findMatches(
                        songs = server,
                        library = own
                    )
                ).filter { (song, match) -> match == null && song.path !in sortedHere }.map { it.first }
                mutableState.value = State(
                    toSort = toSort,
                    missing = missing,
                    isCompared = true
                )
            }
                .onFailure { currentCoroutineContext().ensureActive() }
                .onFailure {
                    Logger.e(it) { "Comparing with the server failed" }
                    mutableState.value = State(
                        result = "Couldn't compare with the server: ${it.message}"
                    )
                }
        }
    }

    /**
     * Sorts the matched songs here and downloads [download] into the music folder.
     */
    fun sync(download: List<Song>) {
        val current = mutableState.value
        if (current.step != null) {
            return
        }
        val total = current.toSort.size + download.size
        scope.launch {
            current.toSort.forEachIndexed { index, match ->
                progress(
                    step = "Sorting",
                    done = index,
                    total = total
                )
                runCatching { sortHere(match) }
                    .onFailure { currentCoroutineContext().ensureActive() }
                    .onFailure { Logger.e(it) { "Sorting ${match.own.path} from the server failed" } }
            }
            val root = musicFolder()
            progress(
                step = "Downloading",
                done = current.toSort.size,
                total = total
            )
            // A few at a time: one request at a time leaves the network idle between small files. Not more, as
            // each song is held in memory until written
            val failed = download.mapParallel(
                parallel = PARALLEL_DOWNLOADS,
                onDone = { progressDone(current.toSort.size + it) }
            ) { song ->
                val downloaded = root != null && runCatching {
                    downloadHere(
                        song = song,
                        root = root
                    )
                }
                    .onFailure { currentCoroutineContext().ensureActive() }
                    .onFailure { Logger.e(it) { "Downloading ${song.path} failed" } }
                    .getOrDefault(false)
                !downloaded
            }.count { it }
            mutableState.value = State(
                result = buildString {
                    append("Sorted ${songs(current.toSort.size)}, downloaded ${songs(download.size - failed)}.")
                    if (failed > 0) {
                        append(" ${songs(failed)} couldn't be downloaded: a file is in the way, or ")
                        append("this device has no music folder.")
                    }
                }
            )
        }
    }

    private fun progress(
        step: String,
        done: Int,
        total: Int,
    ) = mutableState.update {
        it.copy(
            step = step,
            done = done,
            total = total
        )
    }

    private fun progressDone(done: Int) = mutableState.update {
        it.copy(
            done = done
        )
    }

    /**
     * The server's sorted song for each of [songs] that sounds the same, by path; songs that can't be decoded (WMA, Opus) are left out.
     */
    private suspend fun bySound(songs: List<Song>): Map<String, Song> {
        progress(
            step = "Comparing the sound of",
            done = 0,
            total = songs.size
        )
        // Each print decodes two minutes of audio; a few at a time keep the machine responsive
        val prints = songs.mapParallel(
            parallel = PARALLEL_FINGERPRINTS,
            onDone = ::progressDone
        ) { song ->
            SongFingerprints.of(song.path)?.let { song.path to it }
        }.filterNotNull()
        // A few at a time: each request carries the prints, about 8 KB per song
        return prints.chunked(20).flatMap { chunk ->
            chunk.map { it.first }.zip(LibraryClient.sameSound(chunk.map { it.second }))
        }.mapNotNull { (path, match) -> match?.let { path to it } }.toMap()
    }

    /**
     * The server's title, artist, album and cover for [Match.own], filed like a manual save.
     */
    private suspend fun sortHere(match: Match) {
        val server = LibraryClient.songByPath(match.server.path) ?: match.server
        MainViewModel.saveSong(
            song = match.own.copy(
                title = server.title,
                artist = server.artist,
                album = server.album,
                artwork = server.artwork ?: match.own.artwork
            ),
            onThisDevice = true
        )
    }

    /**
     * The server's file where a sorted song belongs, or at the top of [root] when the server hasn't sorted it.
     */
    private suspend fun downloadHere(
        song: Song,
        root: String,
    ): Boolean {
        val extension = song.path.substringAfterLast('.')
        val file = if (song.state == Song.State.Sorted) {
            target(
                root = root,
                song = song.copy(
                    path = "song.$extension"
                )
            )
        } else {
            "${root.trimEnd('/')}/${song.fileName}.$extension"
        }
        val bytes = NetworkManager.downloadSong(song.path)
        // Two songs may aim at the same file; written together, both would pass its check and mix their bytes
        val path = writing.withLock {
            writeSongFile(
                file = file,
                bytes = bytes
            )
        } ?: return false
        val server = LibraryClient.songByPath(song.path) ?: song
        SongRepository.upsert(
            server.copy(
                path = path
            )
        )
        return true
    }
}
