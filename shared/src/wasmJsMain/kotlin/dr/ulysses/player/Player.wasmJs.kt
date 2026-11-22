package dr.ulysses.player

import dr.ulysses.entities.Song
import kotlinx.browser.document
import org.w3c.dom.HTMLAudioElement

private var audioElement: HTMLAudioElement? = null
private var playlist: List<String> = emptyList()
private var currentTrackIndex: Int = 0
private var repeat = RepeatMode.All
private var isPlayingCallback: ((Boolean) -> Unit)? = null
private var currentPlayingCallback: ((String?) -> Unit)? = null

private fun initAudioElement() {
    if (audioElement == null) {
        audioElement = (document.createElement("audio") as HTMLAudioElement).apply {
            id = "audio-player"
            addEventListener(
                type = "play",
                callback = {
                    isPlayingCallback?.invoke(true)
                }
            )

            addEventListener(
                type = "pause",
                callback = {
                    isPlayingCallback?.invoke(false)
                }
            )

            addEventListener(
                type = "ended",
                callback = {
                    when {
                        repeat == RepeatMode.One -> playTrackOnDevice(currentTrackIndex)
                        currentTrackIndex < playlist.size - 1 -> playNextOnDevice()
                        repeat == RepeatMode.All -> playTrackOnDevice(0)
                    }
                }
            )
            style.display = "none"
            document.body?.appendChild(this)
        }
    }
}

actual fun setPlayListOnDevice(songs: List<Song>) {
    initAudioElement()
    playlist = songs.map { it.path }
    currentTrackIndex = 0

    if (playlist.isNotEmpty()) {
        audioElement?.src = playlist[0]
        currentPlayingCallback?.invoke(playlist[0])
    }
}

actual fun removeFromPlayListOnDevice(index: Int) {
    playlist = playlist.filterIndexed { i, _ -> i != index }
    if (index < currentTrackIndex) {
        currentTrackIndex--
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

actual fun setUpcomingOnDevice(
    currentIndex: Int,
    upcoming: List<Song>,
) {
    playlist = playlist.take(currentIndex + 1) + upcoming.map { it.path }
}

actual fun setRepeatOnDevice(mode: RepeatMode) {
    repeat = mode
}
