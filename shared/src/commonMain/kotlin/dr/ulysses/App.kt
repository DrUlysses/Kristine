package dr.ulysses

import androidx.compose.runtime.Composable
import co.touchlab.kermit.*
import co.touchlab.kermit.Logger
import dr.ulysses.theme.AppTheme
import dr.ulysses.ui.views.Main

object Logger : Logger(
    config = loggerConfigInit(
        *listOfNotNull(
            platformLogWriter(NoTagFormatter),
            logFileWriter()
        ).toTypedArray(),
        minSeverity = Severity.Info,
    ),
    tag = "Kristine"
)

/**
 * Writes the log to kristine.log in Kristine's own folder too; null where there are no files (the browser).
 */
internal expect fun logFileWriter(): LogWriter?

@Composable
fun App() = AppTheme {
    Main()
}

internal expect fun openUrl(url: String?)
