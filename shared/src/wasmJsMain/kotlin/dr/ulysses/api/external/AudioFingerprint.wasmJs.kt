package dr.ulysses.api.external

// The browser has no access to the audio files
actual suspend fun audioFingerprint(path: String): AudioFingerprint? = null

actual suspend fun rawFingerprint(path: String): IntArray? = null
