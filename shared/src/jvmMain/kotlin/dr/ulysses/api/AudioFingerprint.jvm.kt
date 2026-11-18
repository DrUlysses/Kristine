package dr.ulysses.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private class FpcalcOutput(val duration: Double, val fingerprint: String)

private val json = Json { ignoreUnknownKeys = true }

// shortcut: needs Chromaprint's fpcalc on PATH; add a path setting if users install it elsewhere
actual suspend fun audioFingerprint(path: String): AudioFingerprint? = withContext(Dispatchers.IO) {
    val process = runCatching { ProcessBuilder("fpcalc", "-json", path.removePrefix("file:///")).start() }
        .getOrElse { return@withContext null } // fpcalc not installed
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) return@withContext null
    json.decodeFromString<FpcalcOutput>(output).let { AudioFingerprint(it.duration.toInt(), it.fingerprint) }
}
