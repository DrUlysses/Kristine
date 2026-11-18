package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

private var audioElement: HTMLAudioElement? = null

// The same element the player in dr.ulysses.player creates
private fun getAudioElement(): HTMLAudioElement {
    if (audioElement == null) {
        audioElement = document.getElementById("audio-player") as? HTMLAudioElement
            ?: (document.createElement("audio") as HTMLAudioElement).apply {
                id = "audio-player"
                style.display = "none"
                document.body?.appendChild(this)
            }
    }
    return audioElement!!
}

@Composable
actual fun VideoPlayer(modifier: Modifier) {
    Box(modifier = modifier) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "Music",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// Seconds, like the song duration the slider compares it with
actual fun getCurrentPosition(): Int = getAudioElement().currentTime.toInt()

actual fun getVolume(): Float = getAudioElement().volume.toFloat()

actual fun setVolume(volume: Float) {
    getAudioElement().volume = volume.toDouble()
}

actual fun isDesktopOrWasm(): Boolean = true
