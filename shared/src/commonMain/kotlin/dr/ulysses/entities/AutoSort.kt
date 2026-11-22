package dr.ulysses.entities

import dr.ulysses.Logger
import dr.ulysses.api.external.MetadataCandidate
import dr.ulysses.api.external.MetadataSearch
import dr.ulysses.api.internal.LibraryTasks
import dr.ulysses.api.internal.SortProposalsCommand
import dr.ulysses.api.internal.StartAutoSortCommand
import dr.ulysses.api.internal.isRemote
import dr.ulysses.models.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

/**
 * An unsorted song and the catalog match it can take without a manual look.
 */
@Serializable
data class Proposal(
    val song: Song,
    val match: MetadataCandidate,
)

/**
 * Looks up every unsorted song in the background, keeping the ones whose best match is close enough to sort in bulk.
 * It keeps running while the user sorts other songs by hand.
 */
object AutoSort {
    @Serializable
    data class State(
        val checked: Int = 0,
        val total: Int = 0,
        /**
         * Every looked up song that got a match, however close.
         */
        val matches: List<Proposal> = emptyList(),
        val isRunning: Boolean = false,
        /**
         * Songs sorted and to sort while sorting; null otherwise.
         */
        val sorting: Pair<Int, Int>? = null,
    ) {
        val proposals
            get() = matches.filter {
                isConfident(
                    song = it.song,
                    match = it.match
                )
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val localState = MutableStateFlow(State())

    /**
     * This device's, or the connected server's.
     */
    val state: StateFlow<State> = LibraryTasks.localOrRemote(
        local = localState,
        remote = LibraryTasks.autoSort
    )
    private var job: Job? = null

    // A matching title and artist (1.0) whose length isn't off, or AcoustID knowing the sound (1.5 × its confidence)
    private const val CONFIDENT_SCORE = 1.1

    private val IN_BRACKETS = Regex("""[(\[][^)\]]*[)\]]""")

    private val ARTIST_SEPARATOR = Regex(
        pattern = """,\s*|\s+(?:&|and|x|feat\.?|ft\.?|featuring)\s+""",
        option = RegexOption.IGNORE_CASE
    )

    private val VERSION_WORDS = setOf(
        "acoustic", "live", "mix", "edit", "nightcore", "instrumental", "cover", "karaoke", "demo", "mashup", "vs",
        "bootleg", "slowed", "sped", "reverb", "acapella", "unplugged", "orchestral"
    )

    private val ACCENTS = listOf("aàáâãäå", "eèéêë", "iìíîï", "oòóôõöø", "uùúûü", "nñ", "cç")
        .flatMap { letters -> letters.drop(1).map { it to letters.first() } }
        .toMap()

    /**
     * Looks up the unsorted songs, unless a lookup is running; songs sorted since drop out of the proposals.
     * A server also searches [clientLibrary], the sorted songs of the client that started it.
     */
    fun start(clientLibrary: List<Song> = emptyList()) {
        if (isRemote) {
            scope.launch { LibraryTasks.send(StartAutoSortCommand(SongRepository.ownSortedSongs())) }
            return
        }
        if (job?.isActive == true) {
            return
        }
        job = scope.launch {
            val songs = SongRepository.getByNotState(Song.State.Sorted).filter { it.state != Song.State.NotDownloaded }
            val known = localState.value.matches.filter { proposal -> songs.any { it.path == proposal.song.path } }
            val toCheck = songs.filter { song -> known.none { it.song.path == song.path } }
            val library = MetadataSearch.candidates(
                songs = SongRepository.ownSortedSongs(),
                source = "Library"
            ) + MetadataSearch.candidates(
                songs = clientLibrary,
                source = "Client library"
            )
            localState.value = State(
                checked = known.size,
                total = songs.size,
                matches = known,
                isRunning = true
            )
            // Deezer allows 50 requests in 5 s and AcoustID 3 a second; each song asks both a few times
            val limit = Semaphore(3)
            toCheck.map { song ->
                launch {
                    limit.withPermit {
                        val match = runCatching {
                            MetadataSearch.find(
                                song = song,
                                library = library
                            ).firstOrNull()
                        }
                            .onFailure { currentCoroutineContext().ensureActive() }
                            .onFailure { Logger.d(it) { "Auto-sort lookup failed for ${song.path}" } }
                            .getOrNull()
                        localState.update {
                            it.copy(
                                checked = it.checked + 1,
                                matches = if (match == null) {
                                    it.matches
                                } else {
                                    it.matches + Proposal(
                                        song = song,
                                        match = match
                                    )
                                }
                            )
                        }
                    }
                }
            }.joinAll()
            localState.update {
                it.copy(
                    isRunning = false
                )
            }
        }
    }

    /**
     * Gives each song its match's title and artist, keeps the album and cover its tags name (the catalog often
     * picks a compilation instead), then files it like a manual save. Runs on when the screen is left.
     */
    fun sort(proposals: List<Proposal>) {
        if (isRemote) {
            LibraryTasks.send(SortProposalsCommand(proposals.map { it.song.path }))
            return
        }
        if (localState.value.sorting != null) {
            return
        }
        localState.update {
            it.copy(
                sorting = 0 to proposals.size
            )
        }
        scope.launch {
            val library = SongRepository.getAllSongs()
            val known = library.mapTo(HashSet()) { it.path }
            proposals.forEachIndexed { index, proposal ->
                val song = proposal.song
                val match = proposal.match
                // A song deleted since its lookup, e.g. as a duplicate, must not come back as an entry without a file
                if (song.path in known) {
                    val ownAlbum = albumOf(song)
                    val artwork = song.artwork.takeIf { ownAlbum != null } ?: match.artworkUrl?.let { url ->
                        runCatching { MetadataSearch.downloadArtwork(url) }
                            .onFailure { currentCoroutineContext().ensureActive() }
                            .getOrNull()
                    }
                    runCatching {
                        MainViewModel.saveSong(
                            inLibrarySpelling(
                                song = song.copy(
                                    title = match.title,
                                    artist = match.artist,
                                    album = ownAlbum ?: match.album,
                                    artwork = artwork ?: song.artwork
                                ),
                                library = library
                            )
                        )
                    }
                        .onFailure { currentCoroutineContext().ensureActive() }
                        .onFailure { Logger.e(it) { "Auto-sorting ${song.path} failed" } }
                }
                localState.update { state ->
                    state.copy(
                        matches = state.matches.filter { it.song.path != song.path },
                        sorting = index + 1 to proposals.size
                    )
                }
            }
            localState.update {
                it.copy(
                    sorting = null
                )
            }
            MainViewModel.loadSongs()
        }
    }

    /**
     * Close enough to sort without a look: a high [MetadataCandidate.score], the same version (no live, acoustic or
     * remix where the file has none, and the other way round), an artist that shares a word with the file's, and no
     * credited artist the file doesn't name.
     */
    private fun isConfident(
        song: Song,
        match: MetadataCandidate,
    ): Boolean {
        if (match.score < CONFIDENT_SCORE) {
            return false
        }
        // The file's artist counts too: a song by "Nightcore" is a sped-up version
        val ownVersions = versions(bracketed(song.fileName) + bracketed(song.title) + " " + song.artist)
        if (ownVersions != versions(bracketed(match.title))) {
            return false
        }
        val ownArtist = words(song.artist)
        val isArtistKnown = ownArtist.isNotEmpty() && !song.artist.trim().equals(
            other = "Unknown artist",
            ignoreCase = true
        )
        if (!isArtistKnown) {
            // Nothing to compare: the sound or the file name found it
            return true
        }
        // The title too: "Title (Artist remix)" by someone else is still the artist's
        val matchWords = words("${match.artist} ${match.title}")
        if (ownArtist.none { it.length > 1 && it in matchWords }) {
            return false
        }
        // Every credited artist named by the file: "Artist, Guest" is often a remix with the guest
        val ownWords = words("${song.fileName} ${song.title} ${song.artist} ${song.album.orEmpty()}")
        return match.artist.split(ARTIST_SEPARATOR).all { credited ->
            words(credited).any { it.length > 1 && it in ownWords }
        }
    }

    // Only words in brackets name a version: "Live" in a title is just a word
    private fun bracketed(text: String) = IN_BRACKETS.findAll(text).joinToString(" ") { it.value }

    private fun versions(text: String) = words(text).map {
        if (it == "remix") {
            "mix"
        } else {
            it
        }
    }.filterTo(HashSet()) { it in VERSION_WORDS }

    /**
     * Lower case words without accents or dots, so "Mötley" is "motley" and "A.B.C." is "abc".
     */
    private fun words(text: String) = text.lowercase()
        .filter { it != '.' && it != '\'' }
        .map { ACCENTS[it] ?: it.takeIf(Char::isLetterOrDigit) ?: ' ' }
        .joinToString("")
        .split(' ')
        .filterTo(HashSet()) { it.isNotEmpty() }
}
