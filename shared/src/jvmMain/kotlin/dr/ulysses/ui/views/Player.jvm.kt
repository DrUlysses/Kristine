package dr.ulysses.ui.views

import dr.ulysses.player.PlayerObject

private val mediaPlayer get() = PlayerObject.mediaPlayer

// Seconds, like the song duration the slider compares it with
actual fun getCurrentPosition(): Int = (mediaPlayer.status().time() / 1000).toInt()

actual fun getVolume(): Float = mediaPlayer.audio().volume() / 100f

actual fun setVolume(volume: Float) {
    mediaPlayer.audio().setVolume((volume * 100).toInt())
}

actual fun isDesktopOrWasm(): Boolean = true
