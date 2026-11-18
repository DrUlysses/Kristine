package dr.ulysses.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VideoPlayer(modifier: Modifier) {
}

// shortcut: Android shows no position or volume slider yet; wire these to PlayerObject.player when it does
actual fun getCurrentPosition(): Int = 0

actual fun getVolume(): Float = 1.0f

actual fun setVolume(volume: Float) {}

actual fun isDesktopOrWasm(): Boolean = false
