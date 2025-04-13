package dr.ulysses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import dr.ulysses.network.NetworkManager
import dr.ulysses.theme.AppTheme
import dr.ulysses.ui.views.Main

@Composable
internal fun App() = AppTheme {
    // Start the network manager when the app starts
    LaunchedEffect(Unit) {
        NetworkManager.start()
    }

    // Clean up when the app is closed
    DisposableEffect(Unit) {
        onDispose {
            NetworkManager.stop()
        }
    }

    Main()
}

internal expect fun openUrl(url: String?)
