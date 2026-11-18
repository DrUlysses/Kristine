package dr.ulysses.player

import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

private var audioElement: HTMLAudioElement? = null
private var playlist: List<String> = emptyList()
private var currentTrackIndex: Int = 0
private var isPlayingCallback: ((Boolean) -> Unit)? = null
private var currentPlayingCallback: ((String?) -> Unit)? = null

private fun initAudioElement() {
    if (audioElement == null) {
        audioElement = (document.createElement("audio") as HTMLAudioElement).apply {
            id = "audio-player"
            addEventListener("play", {
                isPlayingCallback?.invoke(true)
            })

            addEventListener("pause", {
                isPlayingCallback?.invoke(false)
            })

            addEventListener("ended", {
                if (currentTrackIndex < playlist.size - 1) {
                    playNextOnDevice()
                }
            })
            style.display = "none"
            document.body?.appendChild(this)
        }
    }
}

actual fun setPlayListOnDevice(paths: List<String>) {
    initAudioElement()
    playlist = paths
    currentTrackIndex = 0

    if (paths.isNotEmpty()) {
        audioElement?.src = paths[0]
        currentPlayingCallback?.invoke(paths[0])
    }
}

actual fun pauseCurrentSongOnDevice() {
    audioElement?.pause()
}

actual fun resumeCurrentSongOnDevice() {
    audioElement?.play()
}

actual fun stopCurrentSongOnDevice() {
    audioElement?.pause()
    audioElement?.currentTime = 0.0
}

actual fun seekToOnDevice(position: Int) {
    audioElement?.currentTime = position.toDouble() // seconds
}

actual fun isPlayingOnDevice(): Boolean {
    return audioElement?.let { !it.paused } ?: false
}

actual fun isPlayingChangedOnDevice(onChange: (Boolean) -> Unit) {
    isPlayingCallback = onChange
}

actual fun currentPlayingChangedOnDevice(onChange: (String?) -> Unit) {
    currentPlayingCallback = onChange
}

actual fun playTrackOnDevice(trackNum: Int) {
    if (playlist.isNotEmpty() && trackNum in playlist.indices) {
        currentTrackIndex = trackNum
        audioElement?.src = playlist[trackNum]
        currentPlayingCallback?.invoke(playlist[trackNum])
        resumeCurrentSongOnDevice()
    }
}

// Continues after a song ends
private fun playNextOnDevice() {
    if (playlist.isNotEmpty() && currentTrackIndex < playlist.size - 1) {
        playTrackOnDevice(currentTrackIndex + 1)
    }
}
