package dr.ulysses.entities

object Player {
    private val position = 0
    private var currentTrackNum = 0
    var currentSong: Song? = null
    private val currentTrackSequence: LinkedHashMap<Int, Song>? = null
    private val isRemotePlaying = false

    fun setAndPlay(song: Song) {
        currentSong = song
        playSong(song)
    }

    fun play() {
        val song = currentTrackSequence?.get(currentTrackNum)
        if (song != null) {
            currentSong = song
            playSong(song)
        }
    }

    fun pause() {
        pauseCurrentSong()
    }

    fun resume() {
        resumeCurrentSong()
    }

    fun pauseOrResume() {
        if (isPlaying()) pause() else resume()
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
