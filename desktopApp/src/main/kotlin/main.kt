import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dr.ulysses.App
import dr.ulysses.entities.refreshSongs
import dr.ulysses.inject.initKoin
import dr.ulysses.network.NetworkManager
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kristine.shared.generated.resources.Res
import kristine.shared.generated.resources.icon
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension
import java.awt.GraphicsEnvironment

fun main() {
    initKoin {}
    // The connections live as long as the app, not its window
    NetworkManager.start()
    if (GraphicsEnvironment.isHeadless()) {
        // No display, e.g. in a container: only the server, which clients connect to
        runBlocking {
            refreshSongs()
            awaitCancellation()
        }
    }
    application {
        Window(
            title = "Kristine",
            icon = painterResource(Res.drawable.icon),
            onCloseRequest = {
                NetworkManager.stop()
                exitApplication()
            },
            state = rememberWindowState(
                width = 800.dp,
                height = 800.dp
            ),
        ) {
            window.minimumSize = Dimension(300, 300)
            App()
        }
    }
}
