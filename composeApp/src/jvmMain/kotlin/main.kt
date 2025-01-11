import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dr.ulysses.App
import dr.ulysses.inject.initKoin
import kristine.composeapp.generated.resources.Res
import kristine.composeapp.generated.resources.icon
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    initKoin {}
    Window(
        title = "Kristine",
        icon = painterResource(Res.drawable.icon),
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 800.dp, height = 800.dp),
    ) {
        window.minimumSize = Dimension(300, 300)
        App()
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}
