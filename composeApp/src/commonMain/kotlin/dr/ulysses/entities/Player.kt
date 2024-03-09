package dr.ulysses.entities

import kotlin.time.Duration

data class Progress(
    val fraction: Float,
    val time: Duration
)

class Player {
    private val position = 0
    private val currentTrackNum = 0
    var currentChosenArtist: String? = null
    private val currentTrackSequence: LinkedHashMap<Int, Song>? = null

    //    private val preferences: SharedPreferences? = null
//    private val preferencesEditor: SharedPreferences.Editor? = null
//    private val currentContext: Context? = null
    private val isRemotePlaying = false

    fun play() {
        val song = currentTrackSequence?.get(currentTrackNum)
        if (song != null) {
            playSong(song)
        }
    }

    fun pause() {
        pauseCurrentSong()
    }

    fun resume() {
        resumeCurrentSong()
    }

    fun stop() {
        stopCurrentSong()
    }

    fun seekTo(position: Int) {
        seekTo(position)
    }

    fun next() {
        if (currentTrackNum < currentTrackSequence!!.size - 1) {
            currentTrackNum + 1
            playSong(currentTrackSequence[currentTrackNum]!!)
        }
    }

    fun previous() {
        if (currentTrackNum > 0) {
            currentTrackNum - 1
            playSong(currentTrackSequence?.get(currentTrackNum)!!)
        }
    }
}

expect fun playSong(song: Song)

expect fun pauseCurrentSong()

expect fun resumeCurrentSong()

expect fun stopCurrentSong()

expect fun seekTo(position: Int)
