package dr.ulysses.api

// shortcut: no Chromaprint build for Android yet, so songs are only matched by text there
actual suspend fun audioFingerprint(path: String): AudioFingerprint? = null

actual suspend fun rawFingerprint(path: String): IntArray? = null
