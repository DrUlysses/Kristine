import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dr.ulysses.App
import dr.ulysses.inject.initKoin
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin {}
    val body = document.body ?: return
    ComposeViewport(body) {
        App()
    }
}
