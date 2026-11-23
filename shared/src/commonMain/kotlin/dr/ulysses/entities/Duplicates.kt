package dr.ulysses.entities

import app.cash.sqldelight.async.coroutines.awaitAsList
import dr.ulysses.Logger
import dr.ulysses.api.external.MetadataCandidate
import dr.ulysses.api.external.MetadataSearch
import dr.ulysses.api.external.rawFingerprint
import dr.ulysses.api.internal.LibraryClient
import dr.ulysses.api.internal.LibraryTasks
import dr.ulysses.api.internal.isRemote
import dr.ulysses.database.SharedDatabase
import dr.ulysses.ui.views.formatTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Deletes the song file at [path] from the device; false when that isn't possible or allowed. */
expect suspend fun deleteSongFile(path: String): Boolean

/**
 * Copies that sound the same; [best] is the one with the most complete tags and the cleanest file name.
 * [match] is what an online catalog says [best] is; only looked up where a group has several recordings.
 */
@Serializable
data class Recording(
    val copies: List<Song>,
    val match: MetadataCandidate? = null,
) {
    @Transient
    val best: Song = bestOf(copies)
}

/** The copy with the most complete tags and the cleanest file name. */
private fun bestOf(copies: List<Song>): Song = copies.maxBy(::completeness)

// Kept for the app session, so another visit doesn't decode every file and ask the catalogs again.
// shortcut: in memory and by path; a file replaced under the same path keeps its old fingerprint until a restart
private val fingerprints = HashMap<String, IntArray?>()
private val matches = HashMap<String, MetadataCandidate?>()

/**
 * One song in the library more than once, split into [recordings]: a live version is not a copy of the studio one.
 * Its recordings are the same artist and title by an online catalog, or by their tags where it doesn't know them.
 */
@Serializable
data class DuplicateGroup(val recordings: List<Recording>) {
    @Transient
    val songs = recordings.flatMap { it.copies }
}

/** The recording to keep, and why it's suggested. */
data class Suggestion(
    val recording: Recording,
    val reason: String,
)

/**
 * The last search for duplicates, which runs on while its screen is closed.
 */
object DuplicateSearch {
    data class State(
        /**
         * What the search does now; null when it doesn't run.
         */
        val step: String? = null,
        val done: Int = 0,
        val total: Int = 0,
        /**
         * Groups of one recording first: their copies are all the same, so there is nothing to choose.
         */
        val groups: List<DuplicateGroup>? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<State>
        field = MutableStateFlow(State())

    /**
     * Searches again, unless a search runs already.
     */
    fun start() {
        if (state.value.step != null) {
            return
        }
        state.value = State(
            step = "Comparing the sound of"
        )
        scope.launch {
            val groups = runCatching {
                findDuplicates { step, done, total ->
                    state.value = State(
                        step = step,
                        done = done,
                        total = total
                    )
                }
            }
                .onFailure { currentCoroutineContext().ensureActive() }
                .onFailure { Logger.e(it) { "Searching for duplicates failed" } }
                .getOrDefault(emptyList())
            state.value = State(
                groups = groups.sortedBy { it.recordings.size > 1 }
            )
        }
    }
}

/**
 * Groups of at least two songs in the library that are the same song. Candidates share a file name or tags; their
 * sound splits them into recordings, and where those differ, an online catalog decides whether they're one song:
 * two different "01. Intro.mp3" are not.
 */
suspend fun findDuplicates(onProgress: (step: String, done: Int, total: Int) -> Unit): List<DuplicateGroup> {
    if (isRemote) {
        return LibraryTasks.findDuplicates(onProgress)
    }
    val songs = SongRepository.getAllSongs().filter { it.state != Song.State.NotDownloaded }
    val candidates = sameNameOrTags(songs)
    val toCompare = candidates.flatten().filter { it.path !in fingerprints }
    coroutineScope {
        // Each print decodes two minutes of audio; a few at a time keep the machine responsive
        toCompare.chunked(PARALLEL_FINGERPRINTS).forEachIndexed { index, chunk ->
            chunk.map { song -> async(Dispatchers.Default) { song.path to rawFingerprint(song.path) } }
                .awaitAll()
                .forEach { (path, print) -> fingerprints[path] = print }
            onProgress(
                "Comparing the sound of",
                minOf(
                    a = (index + 1) * PARALLEL_FINGERPRINTS,
                    b = toCompare.size
                ),
                toCompare.size
            )
        }
    }
    val bySound = candidates.map { group ->
        sameSound(
            group = group,
            prints = fingerprints
        )
    }

    // Copies that sound the same are one song for sure; only different recordings need the catalog
    val toLookUp = bySound.filter { it.size > 1 }.flatten().map(::bestOf).filter { it.path !in matches }
    coroutineScope {
        // Few at a time: the catalogs limit how often they may be asked
        toLookUp.chunked(PARALLEL_LOOKUPS).forEachIndexed { index, chunk ->
            chunk.map { song -> async { song.path to identify(song) } }
                .awaitAll()
                .forEach { (path, match) -> matches[path] = match }
            onProgress(
                "Looking up online",
                minOf(
                    a = (index + 1) * PARALLEL_LOOKUPS,
                    b = toLookUp.size
                ),
                toLookUp.size
            )
        }
    }
    val keptTogether = KeptTogether.pairs()
    return bySound.flatMap { copiesBySound ->
        val recordings = copiesBySound.map { copies ->
            Recording(
                copies = copies,
                match = matches[bestOf(copies).path]
            )
        }
        if (recordings.size == 1) {
            listOf(DuplicateGroup(recordings))
        } else {
            recordings.groupBy(::identity).values.map(::DuplicateGroup)
        }
    }.filter {
        it.songs.size > 1 && it.hasSomethingToResolve(
            keptTogether = keptTogether
        )
    }
}

/**
 * Identical copies are always resolved; different recordings only while two of them weren't kept together before.
 */
private fun DuplicateGroup.hasSomethingToResolve(keptTogether: Set<Pair<String, String>>): Boolean {
    if (recordings.any { it.copies.size > 1 }) {
        return true
    }
    val paths = recordings.map { it.best.path }
    return paths.indices.any { i ->
        (i + 1..paths.lastIndex).any { j ->
            KeptTogether.pair(
                a = paths[i],
                b = paths[j]
            ) !in keptTogether
        }
    }
}

/** Songs the user kept although they looked like duplicates: they aren't offered for resolving again. */
object KeptTogether : KoinComponent {
    private val sharedDatabase: SharedDatabase by inject()

    suspend fun pairs(): Set<Pair<String, String>> = sharedDatabase { db ->
        db.keptTogetherQueries.selectAll().awaitAsList().mapTo(HashSet()) { it.path_a to it.path_b }
    }

    suspend fun save(paths: List<String>) = sharedDatabase { db ->
        db.keptTogetherQueries.transaction {
            paths.forEachIndexed { i, a ->
                paths.drop(i + 1).forEach { b ->
                    val (first, second) = pair(
                        a = a,
                        b = b
                    )
                    db.keptTogetherQueries.insert(
                        path_a = first,
                        path_b = second
                    )
                }
            }
        }
    }

    /** One order per pair, so a pair is stored and found once. */
    fun pair(
        a: String,
        b: String,
    ) = if (a < b) {
        a to b
    } else {
        b to a
    }
}

/**
 * What a catalog says [song] is: AcoustID's answer by sound when an API key is set, else the best match by name
 * and length on Deezer and iTunes, if it's a good one.
 */
private suspend fun identify(song: Song): MetadataCandidate? {
    val matches = runCatching { MetadataSearch.find(song) }
        .onFailure { currentCoroutineContext().ensureActive() }
        .getOrDefault(emptyList())
    return matches.firstOrNull { it.soundMatch != null }
        ?: matches.firstOrNull()?.takeIf { it.score >= MIN_MATCH_SCORE }
}

/**
 * Artist and title by the catalog, or by the tags where it doesn't know the recording; plus the album tag: these
 * recordings sound different, and on different albums they're versions of the song, like a re-recording.
 */
private fun identity(recording: Recording): String {
    val artist = recording.match?.artist ?: recording.best.artist
    val title = recording.match?.title ?: recording.best.title
    val album = recording.best.album?.takeUnless(::isPlaceholder).orEmpty()
    return "${key(artist)}|${key(title)}|${key(album)}"
}

/** [text] reduced for comparing: no noise like track numbers or "(Official Video)", no case or punctuation. */
private fun key(text: String) = MetadataSearch.normalize(MetadataSearch.clean(text))

/** Tags that say nothing: "Unknown artist", "Unnamed", "Untitled", "Track 3", "3". */
private fun isPlaceholder(text: String): Boolean = PLACEHOLDER.matches(MetadataSearch.normalize(text))

private val PLACEHOLDER = Regex("""|unknown.*|unnamed|untitled|no name|track \d*|\d+""")

/**
 * Keeps the best copy of each of [keep] and deletes every other copy in [group], file and entry. Playlists that
 * held a deleted copy get the kept copy of its recording, or of the first kept one. Several kept recordings are
 * remembered, so they aren't offered as duplicates again. Returns the songs whose file couldn't be deleted: their
 * entries stay, or the next scan would bring them back.
 */
suspend fun resolveDuplicates(
    group: DuplicateGroup,
    keep: List<Recording>,
): List<Song> {
    if (isRemote) {
        return LibraryClient.resolveDuplicates(
            group = group,
            keep = keep
        )
    }
    val kept = keep.map { it.best }
    if (kept.size > 1) {
        KeptTogether.save(kept.map { it.path })
    }
    return group.recordings.flatMap { recording ->
        val keeper = recording.best.takeIf { recording in keep } ?: kept.first()
        recording.copies.filter { it.path != keeper.path }.filter { song ->
            val deleted = deleteSongFile(song.path)
            if (deleted) {
                SongRepository.mergeInto(
                    oldPath = song.path,
                    keepPath = keeper.path
                )
            }
            !deleted
        }
    }
}

/**
 * The recording that matches the song best online (see [Recording.match]). With a single recording, all copies are
 * the same and its best copy stays.
 */
fun suggest(group: DuplicateGroup): Suggestion {
    val single = group.recordings.singleOrNull()
    if (single != null) {
        return Suggestion(
            recording = single,
            reason = "All copies sound the same; this one has the most complete tags"
        )
    }
    val recording = group.recordings.maxBy { it.match?.score ?: -1.0 }
    val match = recording.match
    if (match == null) {
        return Suggestion(
            recording = group.recordings.maxBy { completeness(it.best) },
            reason = "No match online; this one has the most complete tags"
        )
    }
    val reason = match.soundMatch?.let {
        "AcoustID recognizes its sound as ${match.title} – ${match.artist} (${(it * 100).roundToInt()}%)"
    } ?: "Closest to ${match.source}'s ${match.title} – ${match.artist}" +
    match.durationSeconds?.let { " (${formatTime(it)})" }.orEmpty()
    return Suggestion(
        recording = recording,
        reason = reason
    )
}

/** How much a copy tells about itself: filled-in tags, being sorted, and a file name without noise like " (1)". */
private fun completeness(song: Song): Int {
    // The user checked a sorted song's tags
    val sortedBonus = if (song.state == Song.State.Sorted) {
        2
    } else {
        0
    }
    return sortedBonus + listOf(
        !isPlaceholder(song.title),
        !isPlaceholder(song.artist),
        !isPlaceholder(song.album.orEmpty()),
        song.duration != null,
        MetadataSearch.clean(song.fileName) == song.fileName.trim(),
    ).count { it }
}

/** Groups of at least two songs sharing a file name or tags, also through a third song. */
private fun sameNameOrTags(songs: List<Song>): List<List<Song>> {
    val parent = IntArray(songs.size) { it }
    fun root(index: Int): Int {
        var current = index
        while (parent[current] != current) {
            current = parent[current]
        }
        return current
    }

    val byKey = HashMap<String, Int>()
    songs.forEachIndexed { index, song ->
        keys(song).forEach { key ->
            val other = byKey.getOrPut(key) { index }
            parent[root(index)] = root(other)
        }
    }
    return songs.indices.groupBy(::root)
        .values
        .filter { it.size > 1 }
        .map { group -> group.map { songs[it] } }
}

/** The file name without copy markers like " (1)", and artist with title: either one the same marks a candidate. */
private fun keys(song: Song): List<String> {
    val fileName = key(song.fileName)
    val title = key(song.title)
    val artist = key(song.artist)
    return listOfNotNull(
        "file:$fileName".takeIf { !isPlaceholder(fileName) },
        // "Unnamed" by "Unknown artist" is no reason to think two songs are one
        "tags:$artist|$title".takeIf { !isPlaceholder(title) && !isPlaceholder(artist) }
    )
}

/** Splits a group by sound: by fingerprint, or by length where a song has none (e.g. on Android). */
private fun sameSound(
    group: List<Song>,
    prints: Map<String, IntArray?>,
): List<List<Song>> {
    val clusters = mutableListOf<MutableList<Song>>()
    group.forEach { song ->
        val cluster = clusters.find { cluster ->
            soundTheSame(
                a = cluster.first(),
                b = song,
                prints = prints
            )
        }
        if (cluster == null) {
            clusters += mutableListOf(song)
        } else {
            cluster += song
        }
    }
    return clusters
}

private fun soundTheSame(
    a: Song,
    b: Song,
    prints: Map<String, IntArray?>,
): Boolean {
    val printA = prints[a.path]
    val printB = prints[b.path]
    if (printA == null || printB == null) {
        val durationA = a.duration ?: return false
        val durationB = b.duration ?: return false
        return abs(durationA - durationB) <= MAX_DURATION_DIFFERENCE
    }
    return similarity(
        a = printA,
        b = printB
    ) >= SAME_RECORDING
}

/**
 * The share of equal bits at the best alignment of the two fingerprints, shifted by up to [MAX_SHIFT] frames
 * (copies may start a little apart): about 0.5 for unrelated sound, near 1 for the same recording.
 */
internal fun similarity(
    a: IntArray,
    b: IntArray,
): Double {
    var best = 0.0
    for (shift in -MAX_SHIFT..MAX_SHIFT) {
        var equalBits = 0
        var comparedBits = 0
        for (i in a.indices) {
            val j = i + shift
            if (j !in b.indices) {
                continue
            }
            equalBits += Int.SIZE_BITS - (a[i] xor b[j]).countOneBits()
            comparedBits += Int.SIZE_BITS
        }
        if (comparedBits >= MIN_OVERLAP * Int.SIZE_BITS) {
            best = maxOf(
                a = best,
                b = equalBits.toDouble() / comparedBits
            )
        }
    }
    return best
}

private const val PARALLEL_FINGERPRINTS = 4
private const val PARALLEL_LOOKUPS = 2
private const val MIN_MATCH_SCORE = 0.6 // a title and artist that mostly agree, see MetadataSearch.score
internal const val SAME_RECORDING = 0.85
private const val MAX_SHIFT = 10
private const val MIN_OVERLAP = 50 // frames, about 6 seconds
private const val MAX_DURATION_DIFFERENCE = 2 // seconds
