package dr.ulysses.api.external

import dr.ulysses.entities.localFilePath
import dr.ulysses.fingerprint.Fingerprint
import dr.ulysses.fingerprint.fingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun audioFingerprint(path: String): AudioFingerprint? = rustFingerprint(path)?.let {
    AudioFingerprint(
        durationSeconds = it.durationSeconds.toInt(),
        fingerprint = it.compressed
    )
}

actual suspend fun rawFingerprint(path: String): IntArray? = rustFingerprint(path)?.raw?.toIntArray()

/** Decodes the first two minutes of the file; null for formats without a decoder (WMA, Opus) or unreadable files. */
private suspend fun rustFingerprint(path: String): Fingerprint? = withContext(Dispatchers.Default) {
    fingerprint(localFilePath(path))
}
