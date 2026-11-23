import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dr.ulysses.App
import dr.ulysses.inject.initKoin
import dr.ulysses.network.NetworkManager
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin {}
    NetworkManager.start()
    val body = document.body ?: return
    ComposeViewport(body) {
        App()
    }
}
