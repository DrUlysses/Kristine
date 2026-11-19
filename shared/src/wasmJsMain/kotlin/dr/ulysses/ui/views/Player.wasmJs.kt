package dr.ulysses.ui.views

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

// Seconds, like the song duration the slider compares it with
actual fun getCurrentPosition(): Int = getAudioElement().currentTime.toInt()

actual fun getVolume(): Float = getAudioElement().volume.toFloat()

actual fun setVolume(volume: Float) {
    getAudioElement().volume = volume.toDouble()
}

actual fun isDesktopOrWasm(): Boolean = true
