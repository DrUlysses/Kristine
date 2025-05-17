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
        platformLogWriter(NoTagFormatter),
        minSeverity = Severity.Info,
    ),
    tag = "Kristine"
)

@Composable
internal fun App() = AppTheme {
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
