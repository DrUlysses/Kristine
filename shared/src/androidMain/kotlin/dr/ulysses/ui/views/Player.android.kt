package dr.ulysses.ui.views

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import dr.ulysses.player.PlayerObject

@Composable
actual fun VideoPlayer(modifier: Modifier) {
}

// Called from composition, i.e. the main thread, which MediaController requires
@OptIn(UnstableApi::class)
actual fun getCurrentPosition(): Int = ((PlayerObject.player?.currentPosition ?: 0L) / 1000).toInt()

@OptIn(UnstableApi::class)
actual fun getVolume(): Float = PlayerObject.player?.volume ?: 1f

@OptIn(UnstableApi::class)
actual fun setVolume(volume: Float) {
    PlayerObject.player?.volume = volume
}

actual fun isDesktopOrWasm(): Boolean = false
