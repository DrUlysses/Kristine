package dr.ulysses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import co.touchlab.kermit.*
import co.touchlab.kermit.Logger
import dr.ulysses.network.NetworkManager
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
    LaunchedEffect(Unit) {
        NetworkManager.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            NetworkManager.stop()
        }
    }

    Main()
}

internal expect fun openUrl(url: String?)
