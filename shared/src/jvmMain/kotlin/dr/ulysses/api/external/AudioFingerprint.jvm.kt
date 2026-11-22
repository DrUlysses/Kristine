package dr.ulysses.api.external

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class FpcalcOutput(
    val duration: Double,
    val fingerprint: String,
)

// Unsigned 32-bit values, so read as Long; only the bits matter
@Serializable
private class FpcalcRawOutput(
    val fingerprint: LongArray,
)

private val json = Json { ignoreUnknownKeys = true }

actual suspend fun audioFingerprint(path: String): AudioFingerprint? = fpcalc(
    path = path,
    raw = false
)?.let {
    json.decodeFromString<FpcalcOutput>(it).let { output ->
        AudioFingerprint(
            durationSeconds = output.duration.toInt(),
            fingerprint = output.fingerprint
        )
    }
}

actual suspend fun rawFingerprint(path: String): IntArray? = fpcalc(
    path = path,
    raw = true
)?.let { output -> json.decodeFromString<FpcalcRawOutput>(output).fingerprint.map { it.toInt() }.toIntArray() }

/** fpcalc's JSON output, or null when it isn't installed or can't read the file. */
// shortcut: needs Chromaprint's fpcalc on PATH; add a path setting if users install it elsewhere
private suspend fun fpcalc(
    path: String,
    raw: Boolean,
): String? = withContext(Dispatchers.IO) {
    val arguments = listOfNotNull(
        "fpcalc",
        "-json",
        "-raw".takeIf { raw },
        path.removePrefix("file:///")
    )
    val process = runCatching { ProcessBuilder(arguments).start() }.getOrElse { return@withContext null }
    val output = process.inputStream.bufferedReader().readText()
    output.takeIf { process.waitFor() == 0 }
}
