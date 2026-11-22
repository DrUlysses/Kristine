package dr.ulysses.entities

import dr.ulysses.Logger
import dr.ulysses.api.external.LrcLib
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One line of lyrics; [time] in milliseconds from the song's start, null for lyrics without timestamps.
 */
data class LyricLine(
    val time: Long?,
    val text: String,
)

/**
 * Lyrics stored with the song: a .lrc file next to it, else the lyrics tag; null when neither exists or the file
 * isn't on this device (a server's song).
 */
expect suspend fun localLyrics(path: String): String?

/**
 * Writes [lyrics] as a .lrc file next to the song; nothing happens where that isn't possible.
 */
expect suspend fun saveLyricsFile(
    path: String,
    lyrics: String,
)

/**
 * A song's lyrics from its own files, else from LRCLIB, which are then saved next to the song for the next time.
 */
object Lyrics {
    // By path; null = looked up, none found. A failed lookup isn't kept, so it's tried again
    private val known = HashMap<String, List<LyricLine>?>()

    private val TIME = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    // "[ar:Artist]", "[length:3:20]": an .lrc file's information, not lyrics
    private val INFO = Regex("""^\[[a-zA-Z]+:.*]$""")

    /**
     * The lines in time order, or null when there are no lyrics.
     */
    suspend fun of(song: Song): List<LyricLine>? {
        if (song.path in known) {
            return known[song.path]
        }
        // Synced lyrics win wherever they are: tags often hold plain ones while LRCLIB has them with times
        val local = localLyrics(song.path)?.let(::parse)?.takeIf { it.isNotEmpty() }
        if (local?.first()?.time != null) {
            return local.also { known[song.path] = it }
        }
        val online = runCatching { LrcLib.find(song) }
            .onFailure { currentCoroutineContext().ensureActive() }
            .onFailure { Logger.d(it) { "Lyrics lookup failed for ${song.path}" } }
            .getOrElse { return local }
        val parsed = online?.let(::parse)?.takeIf { it.isNotEmpty() }
        if (online != null && parsed != null && (local == null || parsed.first().time != null)) {
            saveLyricsFile(
                path = song.path,
                lyrics = online
            )
        }
        val best = if (parsed?.first()?.time != null) {
            parsed
        } else {
            local ?: parsed
        }
        return best.also { known[song.path] = it }
    }

    /**
     * LRC lines (a line may carry several times), or the plain lines when there are no times.
     */
    internal fun parse(text: String): List<LyricLine> {
        val lines = text.lines()
        val timed = lines.flatMap { line ->
            val words = line.replace(
                regex = TIME,
                replacement = ""
            ).trim()
            TIME.findAll(line).map { stamp ->
                val (minutes, seconds, fraction) = stamp.destructured
                LyricLine(
                    // "xx" are hundredths, "xxx" thousandths
                    time = (minutes.toLong() * 60 + seconds.toLong()) * 1000 + fraction.padEnd(3, '0').toLong(),
                    text = words
                )
            }.toList()
        }
        if (timed.isNotEmpty()) {
            return timed.sortedBy { it.time }
        }
        return lines.map { it.trim() }.filter { it.isNotEmpty() && !INFO.matches(it) }.map {
            LyricLine(
                time = null,
                text = it
            )
        }
    }
}
