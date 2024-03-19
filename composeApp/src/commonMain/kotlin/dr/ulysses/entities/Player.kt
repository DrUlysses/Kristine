package dr.ulysses.entities

import kotlin.time.Duration

data class Progress(
    val fraction: Float,
    val time: Duration
)

object Player {
    private val position = 0
    private var currentTrackNum = 0
    var currentSong: Song? = null
    private val currentTrackSequence: LinkedHashMap<Int, Song>? = null

    //    private val preferences: SharedPreferences? = null
//    private val preferencesEditor: SharedPreferences.Editor? = null
//    private val currentContext: Context? = null
    private val isRemotePlaying = false

    fun play() {
        val song = currentTrackSequence?.get(currentTrackNum)
        if (song != null) {
            currentSong = song
            playSong(song)
        }
    }

    fun playing(): Boolean {
        return isPlaying()
    }

    fun pause() {
        pauseCurrentSong()
    }

    fun resume() {
        resumeCurrentSong()
    }

    fun stop() {
        stopCurrentSong()
        currentSong = null
    }

    fun seekTo(position: Int) {
        seekTo(position)
    }

    fun next() {
        if (currentTrackNum < currentTrackSequence!!.size - 1) {
            currentTrackSequence[currentTrackNum]?.let {
                currentSong = it
                currentTrackNum += 1
                playSong(it)
            }
        }
    }

    fun previous() {
        if (currentTrackNum > 0) {
            currentTrackNum - 1
            currentTrackSequence?.get(currentTrackNum)?.let {
                currentSong = it
                playSong(it)
            }
        }
    }
}

expect fun playSong(song: Song)

expect fun pauseCurrentSong()

expect fun isPlaying(): Boolean

expect fun resumeCurrentSong()

expect fun stopCurrentSong()

expect fun seekTo(position: Int)
