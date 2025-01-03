import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dr.ulysses.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
//    CanvasBasedWindow(canvasElementId = "ComposeTarget") { App() }
    val body = document.body ?: return
    ComposeViewport(body) {
        App()
    }
}
