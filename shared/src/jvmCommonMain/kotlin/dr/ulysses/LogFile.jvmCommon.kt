package dr.ulysses

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import dr.ulysses.network.appDataDirectory
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.time.Clock

internal actual fun logFileWriter(): LogWriter? = runCatching {
    val file = Path(appDataDirectory(), "kristine.log")
    // One older file is kept, so the log can't grow without end
    if ((SystemFileSystem.metadataOrNull(file)?.size ?: 0) > MAX_LOG_SIZE) {
        SystemFileSystem.atomicMove(
            source = file,
            destination = Path("$file.1")
        )
    }
    FileLogWriter(file)
}.getOrNull()

/**
 * Appends every line to [file], with the time and the stack trace of its exception.
 */
private class FileLogWriter(private val file: Path) : LogWriter() {
    private val lock = Any()

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        val line = buildString {
            append("${Clock.System.now()} ${severity.name.uppercase()} $message\n")
            throwable?.let { append(it.stackTraceToString()) }
        }
        // A failing log write must never break what was being logged
        runCatching {
            synchronized(lock) {
                SystemFileSystem.sink(
                    path = file,
                    append = true
                ).buffered().use { it.writeString(line) }
            }
        }
    }
}

private const val MAX_LOG_SIZE = 5L * 1024 * 1024
