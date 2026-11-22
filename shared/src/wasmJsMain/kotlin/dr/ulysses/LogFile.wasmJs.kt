package dr.ulysses

import co.touchlab.kermit.LogWriter

// The browser has no files to write; its console has the log
internal actual fun logFileWriter(): LogWriter? = null
