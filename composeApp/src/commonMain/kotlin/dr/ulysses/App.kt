package dr.ulysses

import androidx.compose.runtime.Composable
import dr.ulysses.theme.AppTheme
import dr.ulysses.ui.permissions.requestPermissions
import dr.ulysses.ui.views.Main

@Composable
internal fun App() = AppTheme {
    Main()
    requestPermissions()
}

internal expect fun openUrl(url: String?)
