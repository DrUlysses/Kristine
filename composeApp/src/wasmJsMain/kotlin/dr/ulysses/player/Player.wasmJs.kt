package dr.ulysses.player

import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

// Audio element for playing media
private var audioElement: HTMLAudioElement? = null

// Current playlist
private var playlist: List<String> = emptyList()

// Current track index
private var currentTrackIndex: Int = 0

// Callbacks
private var isPlayingCallback: ((Boolean) -> Unit)? = null
private var currentPlayingCallback: ((String?) -> Unit)? = null

// Initialize the audio element
private fun initAudioElement() {
    if (audioElement == null) {
        audioElement = (document.createElement("audio") as HTMLAudioElement).apply {
            id = "audio-player"

            // Add event listeners
            addEventListener("play", {
                isPlayingCallback?.invoke(true)
            })

            addEventListener("pause", {
                isPlayingCallback?.invoke(false)
            })

            addEventListener("ended", {
                // Auto play next track when current one ends
                if (currentTrackIndex < playlist.size - 1) {
                    playNextOnDevice()
                }
            })

            // Append to document body but hide it
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
    audioElement?.currentTime = position / 1000.0 // Convert milliseconds to seconds
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

actual fun setCurrentTrackNumOnDevice(trackNum: Int) {
    if (playlist.isNotEmpty() && trackNum in playlist.indices) {
        currentTrackIndex = trackNum
        audioElement?.src = playlist[trackNum]
        currentPlayingCallback?.invoke(playlist[trackNum])
        resumeCurrentSongOnDevice()
    }
}

actual fun playNextOnDevice() {
    if (playlist.isNotEmpty() && currentTrackIndex < playlist.size - 1) {
        setCurrentTrackNumOnDevice(currentTrackIndex + 1)
    }
}

actual fun playPreviousOnDevice() {
    if (playlist.isNotEmpty() && currentTrackIndex > 0) {
        setCurrentTrackNumOnDevice(currentTrackIndex - 1)
    }
}
