package dr.ulysses

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dr.ulysses.inject.initKoin
import java.awt.Dimension
import java.nio.file.Paths

fun main() = application {
    initKoin {}
    Window(
        title = "Kristine",
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 300.dp, height = 300.dp),
    ) {
        window.minimumSize = Dimension(300, 300)
//        val appPath = AppDirsFactory.getInstance().getUserDataDir("Kristine", "0.1", "dr.ulysses")
        val appPath = Paths.get("").toAbsolutePath().parent.toString()
        App()
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}
