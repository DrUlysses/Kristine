package dr.ulysses.ui.views

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dr.ulysses.player.Player
import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

// Audio element for controlling media playback
private var audioElement: HTMLAudioElement? = null

// Initialize the audio element
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
    val currentSong = Player.state.currentSong

    Box(modifier = modifier) {
        // Display music note icon
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "Music",
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// Platform-specific implementations for player control
actual fun getCurrentPosition(): Int {
    return try {
        val audio = getAudioElement()
        (audio.currentTime * 1000).toInt() // Convert seconds to milliseconds
    } catch (e: Exception) {
        0
    }
}

actual fun getVolume(): Float {
    return try {
        getAudioElement().volume.toFloat()
    } catch (e: Exception) {
        1.0f
    }
}

actual fun setVolume(volume: Float) {
    try {
        getAudioElement().volume = volume.toDouble()
    } catch (_: Exception) {
        // Handle exception
    }
}

actual fun isDesktopOrWasm(): Boolean = true
