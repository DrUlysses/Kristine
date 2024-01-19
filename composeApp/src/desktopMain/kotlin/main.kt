
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dr.ulysses.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 300.dp, height = 300.dp),
        title = "Kristine"
    ) {
        App()
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}
