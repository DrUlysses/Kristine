package dr.ulysses.entities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The folder sorted songs are filed in; null where song files can't be moved (the browser).
 */
expect suspend fun musicFolder(): String?

/**
 * Moves the song file at [from] to [to], creating its folders; false when that isn't possible or allowed.
 */
expect suspend fun moveSongFile(
    from: String,
    to: String,
): Boolean

/**
 * A sorted song whose file goes to [target], in the same path form as the song's own path.
 */
class Move(
    val song: Song,
    val target: String,
)

/**
 * What organizing would do: the moves into [root], and the songs it leaves where they are, with why.
 */
class OrganizePlan(
    val root: String,
    val moves: List<Move>,
    val skipped: List<Pair<Song, String>>,
)

/**
 * Sorted songs not yet at <music folder>/<Artist>/<Album>/<Title>.<ext> (without the album folder when the album
 * is unknown). A song stays where it is when its target is taken, by a file or by another song moving there.
 */
suspend fun planOrganize(): OrganizePlan? {
    val root = musicFolder() ?: return null
    val moves = SongRepository.getAllSongs()
        .filter { it.state == Song.State.Sorted }
        .map {
            Move(
                song = it,
                target = target(
                    root = root,
                    song = it
                )
            )
        }
        .filter { it.target != it.song.path }
    val sameTarget = moves.groupBy { it.target }.filterValues { it.size > 1 }.keys
    // A disk check per song: kept off the UI thread
    val (taken, free) = withContext(Dispatchers.Default) {
        moves.partition { it.target in sameTarget || fileExists(it.target) }
    }
    return OrganizePlan(
        root = root,
        moves = free,
        skipped = taken.map { move ->
            move.song to if (move.target in sameTarget) {
                "another sorted song has the same artist, album and title"
            } else {
                "a file is already at ${move.target.removePrefix("file://")}"
            }
        }
    )
}

/**
 * Moves the files and their entries; returns the moves that failed, whose files and entries stay as they were.
 */
suspend fun organize(moves: List<Move>): List<Move> = moves.filter { move ->
    val moved = moveSongFile(
        from = move.song.path,
        to = move.target
    )
    if (moved) {
        SongRepository.move(
            oldPath = move.song.path,
            newPath = move.target
        )
    }
    !moved
}

/**
 * Moves a song just sorted to where [planOrganize] would, and returns its path afterwards: the old one when it
 * stays, because files can't be moved here, it is there already, or its target is taken.
 */
suspend fun fileSorted(song: Song): String {
    val root = musicFolder() ?: return song.path
    val target = target(
        root = root,
        song = song
    )
    if (target == song.path || withContext(Dispatchers.Default) { fileExists(target) }) {
        return song.path
    }
    val failed = organize(
        listOf(
            Move(
                song = song,
                target = target
            )
        )
    )
    return if (failed.isEmpty()) {
        target
    } else {
        song.path
    }
}

/**
 * Where [song] belongs, written like its own path: desktop paths start with file:///, Android paths don't.
 */
private fun target(
    root: String,
    song: Song,
): String {
    val prefix = if (song.path.startsWith("file:///")) {
        "file:///"
    } else {
        ""
    }
    val extension = song.path.substringAfterLast(
        delimiter = '.',
        missingDelimiterValue = ""
    )
    // The main artist, so a song with a guest is in the artist's folder
    val folders = listOfNotNull(
        root.trimEnd('/'),
        folderName(artistOf(song).ifBlank { "Unknown artist" }),
        albumOf(song)?.let(::folderName)
    )
    return prefix + (folders + "${folderName(song.title)}.$extension").joinToString("/")
}

/**
 * [name] as a file or folder name on every file system: no separators or reserved characters, no trailing dots.
 */
private fun folderName(name: String) = name.replace(
    regex = UNSAFE_IN_NAME,
    replacement = "_"
).trim().trimEnd('.').ifEmpty { "_" }

private val UNSAFE_IN_NAME = Regex("""[/\\:*?"<>|]""")
