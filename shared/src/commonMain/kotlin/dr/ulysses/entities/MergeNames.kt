package dr.ulysses.entities

import dr.ulysses.Logger
import dr.ulysses.api.internal.LibraryTasks
import dr.ulysses.api.internal.MergeIntoCommand
import dr.ulysses.api.internal.MergeNamesCommand
import dr.ulysses.api.internal.isRemote
import dr.ulysses.models.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One artist or album whose songs spell it differently, to be written one way: "A & B" and "A And B", or "Artist"
 * and "Artist, Guest", whose featured artist goes to the title.
 */
data class NameGroup(
    val isAlbum: Boolean,
    /**
     * The main artist, of the album too.
     */
    val artist: String,
    val songs: List<Song>,
    /**
     * Each spelling the songs use (of an album without edition notes) and how many use it, the most used first.
     */
    val spellings: List<Pair<String, Int>>,
) {
    val suggestion = spellings.first().first

    /**
     * The same for the group after other merges, which change spellings and paths but not what is grouped.
     */
    val id = if (isAlbum) {
        "album|${nameKey(artist)}|${nameKey(albumTitleOf(songs.first())!!)}"
    } else {
        "artist|${nameKey(artist)}"
    }
}

/**
 * The artists and albums of [library] spelled more than one way, the ones with the most songs first.
 */
fun findNameGroups(library: List<Song>): List<NameGroup> {
    val artists = library.groupBy { nameKey(artistOf(it)) }
        .filterKeys { it.isNotEmpty() }
        .values
        .mapNotNull { songs ->
            val spellings = spellings(songs.map(::artistOf))
            // One spelling, but credits that name more than the main artist still need merging
            if (spellings.size < 2 && songs.none { featuredOf(it).isNotEmpty() }) {
                return@mapNotNull null
            }
            NameGroup(
                isAlbum = false,
                artist = spellings.first().first,
                songs = songs,
                spellings = spellings
            )
        }
    val albums = library.filter { albumOf(it) != null }
        .groupBy { "${nameKey(albumTitleOf(it)!!)}|${nameKey(artistOf(it))}" }
        .values
        .mapNotNull { songs ->
            // Offered without edition notes, counted together: "Album (Deluxe)" counts for "Album"
            val spellings = spellings(songs.mapNotNull(::albumTitleOf))
            if (songs.mapNotNull(::albumOf).distinct().size < 2) {
                return@mapNotNull null
            }
            NameGroup(
                isAlbum = true,
                artist = mostCommon(songs.map(::artistOf)),
                songs = songs,
                spellings = spellings
            )
        }
    return (artists + albums).sortedByDescending { it.songs.size }
}

/**
 * Writes merged names into the songs' tags in the background, so leaving the screen doesn't stop it halfway. The songs
 * keep their state; sorted ones move to the folder of their new name.
 */
object NameMerger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val localProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    /**
     * Steps done and to do while merging, null otherwise.
     */
    val progress: StateFlow<Pair<Int, Int>?> = LibraryTasks.localOrRemote(
        local = localProgress,
        remote = LibraryTasks.merging
    )

    /**
     * Merges each group with [ids] into its chosen name, or its [NameGroup.suggestion] when [names] has none. Each
     * group is found again first: a merge before it may have moved its songs.
     */
    fun merge(
        ids: List<String>,
        names: Map<String, String> = emptyMap(),
    ) {
        if (isRemote) {
            LibraryTasks.send(
                MergeNamesCommand(
                    ids = ids,
                    names = names
                )
            )
            return
        }
        run(ids) { id ->
            val group = findNameGroups(SongRepository.getAllSongs()).find { it.id == id } ?: return@run
            val name = names[id] ?: group.suggestion
            group.songs.forEach { song ->
                retag(song) {
                    if (group.isAlbum) {
                        copy(
                            album = name
                        )
                    } else {
                        copy(
                            artist = name,
                            title = titleWithFeatured(song)
                        )
                    }
                }
            }
        }
    }

    /**
     * Gives [songs] the album [album], for spellings the rules don't catch.
     */
    fun mergeInto(
        songs: List<Song>,
        album: String,
    ) {
        if (isRemote) {
            LibraryTasks.send(
                MergeIntoCommand(
                    songs = songs,
                    album = album
                )
            )
            return
        }
        run(listOf(album)) {
            songs.forEach { song ->
                retag(song) {
                    copy(
                        album = album
                    )
                }
            }
        }
    }

    /**
     * Does [step] for each of [items] one after the other, unless a merge is running.
     */
    private fun <T> run(
        items: List<T>,
        step: suspend (T) -> Unit,
    ) {
        if (localProgress.value != null) {
            return
        }
        localProgress.value = 0 to items.size
        scope.launch {
            items.forEachIndexed { index, item ->
                runCatching { step(item) }
                    .onFailure { currentCoroutineContext().ensureActive() }
                    .onFailure { Logger.e(it) { "Merging $item failed" } }
                localProgress.value = index + 1 to items.size
            }
            localProgress.value = null
            MainViewModel.loadSongs()
        }
    }

    /**
     * Saves the tags [edit] changes; a song they don't change isn't written at all.
     */
    private suspend fun retag(
        song: Song,
        edit: Song.() -> Song,
    ) {
        val edited = song.edit()
        if (edited.title == song.title && edited.artist == song.artist && edited.album == song.album) {
            return
        }
        MainViewModel.saveSong(
            // The library's copy has no artwork; saving it without would erase the stored one
            song = edited.copy(
                artwork = SongRepository.getArtwork(song.path)
            ),
            sort = false
        )
    }
}

/**
 * [song] as the library already writes its artist and album, so a song added later doesn't bring back a spelling
 * that was merged away: only the main artist, featured ones in the title.
 */
fun inLibrarySpelling(
    song: Song,
    library: List<Song>,
): Song {
    val artistKey = nameKey(artistOf(song))
    val byArtist = library.filter { nameKey(artistOf(it)) == artistKey }
    val albumKey = albumTitleOf(song)?.let(::nameKey)
    val sameAlbum = byArtist.filter { other -> albumKey != null && albumTitleOf(other)?.let(::nameKey) == albumKey }
    return song.copy(
        artist = byArtist.takeIf { it.isNotEmpty() }?.let { mostCommon(it.map(::artistOf)) } ?: artistOf(song),
        title = titleWithFeatured(song),
        // Without edition notes, as merging writes it
        album = sameAlbum.takeIf { it.isNotEmpty() }?.let { mostCommon(it.mapNotNull(::albumTitleOf)) } ?: song.album
    )
}

/**
 * "Title" by "Artist, Guest" becomes "Title (feat. Guest)", unless the title names them already.
 */
private fun titleWithFeatured(song: Song): String {
    val featured = featuredOf(song)
    val named = FEAT_IN_TITLE.containsMatchIn(song.title) || featured.all {
        song.title.contains(
            other = it,
            ignoreCase = true
        )
    }
    return if (featured.isEmpty() || named) {
        song.title
    } else {
        "${song.title} (feat. ${featured.joinToString(" & ")})"
    }
}

private fun spellings(names: List<String>) =
    names.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

private val FEAT_IN_TITLE = Regex(
    pattern = """\b(feat\.?|ft\.|featuring)\s""",
    option = RegexOption.IGNORE_CASE
)
