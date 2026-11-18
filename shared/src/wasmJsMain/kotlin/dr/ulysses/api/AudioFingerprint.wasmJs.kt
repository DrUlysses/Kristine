package dr.ulysses.api

// The browser has no access to the audio files
actual suspend fun audioFingerprint(path: String): AudioFingerprint? = null
