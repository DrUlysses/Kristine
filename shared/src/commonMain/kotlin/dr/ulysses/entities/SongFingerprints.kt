package dr.ulysses.entities

import dr.ulysses.api.external.rawFingerprint
import dr.ulysses.api.internal.LibraryClient
import dr.ulysses.api.internal.isRemote
import kotlinx.coroutines.*

/**
 * Raw fingerprints kept in the library, so Sync compares sounds without decoding every file again.
 */
// shortcut: kept by path; a file replaced under the same path keeps its old print, clear the column if that happens
object SongFingerprints {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /**
     * The stored print of the song at [path], computed and stored first when it has none.
     */
    suspend fun of(path: String): IntArray? = SongRepository.fingerprint(path) ?: rawFingerprint(path)?.also {
        SongRepository.saveFingerprint(
            path = path,
            fingerprint = it
        )
    }

    /**
     * Computes the missing prints after a scan, unless that runs already; the first run decodes the whole library.
     */
    fun fillInBackground() {
        if (job?.isActive == true) {
            return
        }
        job = scope.launch {
            // fpcalc decodes the whole file; two at a time keep the machine responsive
            SongRepository.pathsWithoutFingerprint().chunked(2).forEach { chunk ->
                chunk.map { path -> async { runCatching { of(path) } } }.awaitAll()
            }
        }
    }

    /**
     * The song in [songs] that sounds like [print], by the prints in [stored], or null.
     */
    fun sameSound(
        print: IntArray,
        songs: List<Song>,
        stored: Map<String, IntArray>,
    ): Song? {
        val scores = sameSoundScores(
            print = print,
            songs = songs,
            stored = stored
        )
        return songs.filter { it.path in scores }.maxByOrNull { scores.getValue(it.path) }
    }

    /**
     * How alike [print] and the stored prints of [songs] are (0.85 to 1), by path, for the ones that sound the same.
     */
    fun sameSoundScores(
        print: IntArray,
        songs: List<Song>,
        stored: Map<String, IntArray>,
    ): Map<String, Double> = songs.mapNotNull { song ->
        stored[song.path]?.let {
            song.path to similarity(
                a = print,
                b = it
            )
        }
    }.filter { it.second >= SAME_RECORDING }.toMap()

    /**
     * Which of this device's [songs] sound like [song], also when [song] is a connected server's: its print then
     * comes from the server. Empty where `fpcalc` is missing.
     */
    suspend fun ownSameSound(
        song: Song,
        songs: List<Song>,
    ): Map<String, Double> {
        val print = if (isRemote) {
            LibraryClient.rawFingerprint(song.path)
        } else {
            of(song.path)
        } ?: return emptyMap()
        return sameSoundScores(
            print = print,
            songs = songs,
            stored = SongRepository.fingerprints()
        )
    }
}

/**
 * [print] as 4 bytes per value, for the DB.
 */
internal fun bytesOf(print: IntArray): ByteArray = ByteArray(print.size * 4) { i ->
    (print[i / 4] shr (8 * (i % 4))).toByte()
}

internal fun printOf(bytes: ByteArray): IntArray = IntArray(bytes.size / 4) { i ->
    (0 until 4).fold(0) { value, b -> value or ((bytes[i * 4 + b].toInt() and 0xFF) shl (8 * b)) }
}
