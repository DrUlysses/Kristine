package dr.ulysses

import androidx.compose.runtime.Composable
import dr.ulysses.theme.AppTheme
import dr.ulysses.ui.views.Main
import io.ktor.server.cio.*
import io.ktor.server.engine.*

@Composable
internal fun App() = AppTheme {
    val server = embeddedServer(
        factory = CIO,
        host = "127.0.0.1",
        port = 0
    ) {}
    Main()
}

internal expect fun openUrl(url: String?)
